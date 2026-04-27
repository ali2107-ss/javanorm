package ru.normacontrol.application.usecase;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.normacontrol.application.dto.response.DocumentResponse;
import ru.normacontrol.application.mapper.DocumentMapper;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.enums.DocumentStatus;
import ru.normacontrol.domain.event.DocumentUploadedEvent;
import ru.normacontrol.domain.event.DomainEventPublisher;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.domain.repository.WriteDocumentRepository;
import ru.normacontrol.infrastructure.audit.AuditLogged;
import ru.normacontrol.infrastructure.minio.MinioStorageService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Use case for document upload, retrieval and soft deletion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUseCase {

    private final ReadDocumentRepository readDocumentRepository;
    private final WriteDocumentRepository writeDocumentRepository;
    private final CheckResultRepository checkResultRepository;
    private final MinioStorageService storageService;
    private final DocumentMapper documentMapper;
    private final DomainEventPublisher domainEventPublisher;
    private final CheckDocumentUseCase checkDocumentUseCase;

    /**
     * Upload a document and enqueue it for checking.
     *
     * @param file uploaded file
     * @param ownerId document owner
     * @return created document DTO
     */
    @Transactional
    @AuditLogged(action = "UPLOAD_DOCUMENT", resourceType = "DOCUMENT")
    public DocumentResponse execute(MultipartFile file, UUID ownerId) {
        String storageKey = UUID.randomUUID() + "_" + file.getOriginalFilename();
        storageService.uploadFile(storageKey, file);

        Document document = Document.builder()
                .id(UUID.randomUUID())
                .originalFilename(file.getOriginalFilename())
                .storageKey(storageKey)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .status(DocumentStatus.UPLOADED)
                .ownerId(ownerId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Document saved = writeDocumentRepository.save(document);
        domainEventPublisher.publish(new DocumentUploadedEvent(saved.getId(), ownerId, saved.getFileName()));
        checkDocumentUseCase.initiateCheck(saved.getId(), ownerId);
        return documentMapper.toResponse(saved);
    }

    /**
     * Backward-compatible alias for upload execution.
     *
     * @param file uploaded file
     * @param ownerId owner identifier
     * @return created document response
     */
    @Transactional
    public DocumentResponse uploadAndCheck(MultipartFile file, UUID ownerId) {
        return execute(file, ownerId);
    }

    /**
     * Get a document by identifier.
     *
     * @param documentId document identifier
     * @param requesterId requesting user
     * @return document DTO
     */
    @Transactional(readOnly = true)
    public DocumentResponse getById(UUID documentId, UUID requesterId) {
        Document document = requireOwnedDocument(documentId, requesterId);
        if (document.getStatus() == DocumentStatus.DELETED) {
            throw new EntityNotFoundException("Документ удалён: " + documentId);
        }
        return documentMapper.toResponse(document);
    }

    /**
     * Get all active documents for an owner.
     *
     * @param ownerId owner identifier
     * @return owner documents
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> getByOwner(UUID ownerId) {
        return readDocumentRepository.findByOwnerId(ownerId).stream()
                .filter(document -> document.getStatus() != DocumentStatus.DELETED)
                .map(documentMapper::toResponse)
                .toList();
    }

    /**
     * Soft-delete a document.
     *
     * @param documentId document identifier
     * @param requesterId requesting user
     */
    @Transactional
    @AuditLogged(action = "DELETE_DOCUMENT", resourceType = "DOCUMENT")
    public void delete(UUID documentId, UUID requesterId) {
        Document document = requireOwnedDocument(documentId, requesterId);
        domainEventPublisher.publish(document.softDelete(requesterId));
        writeDocumentRepository.save(document);
        log.info("Document {} soft-deleted by {}", documentId, requesterId);
    }

    /**
     * Download the latest generated report for a document.
     *
     * @param documentId document identifier
     * @param requesterId requesting user
     * @return PDF bytes
     */
    @Transactional(readOnly = true)
    @AuditLogged(action = "EXPORT_REPORT", resourceType = "CHECK_RESULT")
    public byte[] downloadLatestReport(UUID documentId, UUID requesterId) {
        requireOwnedDocument(documentId, requesterId);
        CheckResult result = checkResultRepository.findLatestByDocumentId(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Результат проверки не найден: " + documentId));
        if (result.getReportStoragePath() == null || result.getReportStoragePath().isBlank()) {
            throw new EntityNotFoundException("PDF-отчёт ещё не сформирован");
        }
        return storageService.downloadBytes(result.getReportStoragePath());
    }

    private Document requireOwnedDocument(UUID documentId, UUID requesterId) {
        Document document = readDocumentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Документ не найден: " + documentId));
        if (!document.getOwnerId().equals(requesterId)) {
            throw new SecurityException("Нет доступа к документу");
        }
        return document;
    }
}
