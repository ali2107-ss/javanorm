package ru.normacontrol.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.normacontrol.application.dto.response.DocumentResponse;
import ru.normacontrol.application.mapper.DocumentMapper;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.enums.DocumentStatus;
import ru.normacontrol.domain.repository.DocumentRepository;
import ru.normacontrol.infrastructure.kafka.producer.DocumentCheckProducer;
import ru.normacontrol.infrastructure.minio.MinioStorageService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Use Case: Операции с документами (загрузка, получение, удаление, постановка в очередь).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUseCase {

    private final DocumentRepository documentRepository;
    private final MinioStorageService storageService;
    private final DocumentCheckProducer checkProducer;
    private final DocumentMapper documentMapper;

    /**
     * Загрузить документ и поставить его в очередь на проверку.
     */
    @Transactional
    public DocumentResponse uploadAndCheck(MultipartFile file, UUID ownerId) {
        // Валидация типа файла
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.contains("pdf")
                && !contentType.contains("wordprocessingml")
                && !contentType.contains("msword"))) {
            throw new IllegalArgumentException(
                    "Поддерживаются только файлы PDF и DOCX");
        }

        // Генерация уникального ключа хранилища
        String storageKey = UUID.randomUUID() + "_" + file.getOriginalFilename();

        // Загрузка в MinIO
        storageService.uploadFile(storageKey, file);

        // Создание записи документа
        Document document = Document.builder()
                .id(UUID.randomUUID())
                .originalFilename(file.getOriginalFilename())
                .storageKey(storageKey)
                .contentType(contentType)
                .fileSize(file.getSize())
                .status(DocumentStatus.UPLOADED)
                .ownerId(ownerId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        document.enqueue();
        document = documentRepository.save(document);

        // Отправка в Kafka для асинхронной проверки
        checkProducer.sendCheckRequest(document.getId(), ownerId);
        log.info("Документ {} загружен и поставлен в очередь на проверку", document.getId());

        return documentMapper.toResponse(document);
    }

    /**
     * Получить документ по ID.
     */
    @Transactional(readOnly = true)
    public DocumentResponse getById(UUID documentId, UUID requesterId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Документ не найден: " + documentId));

        if (!doc.getOwnerId().equals(requesterId)) {
            throw new SecurityException("Нет доступа к документу");
        }

        return documentMapper.toResponse(doc);
    }

    /**
     * Получить все документы пользователя.
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> getByOwner(UUID ownerId) {
        return documentRepository.findByOwnerId(ownerId).stream()
                .map(documentMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Удалить документ.
     */
    @Transactional
    public void delete(UUID documentId, UUID requesterId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Документ не найден: " + documentId));

        if (!doc.getOwnerId().equals(requesterId)) {
            throw new SecurityException("Нет доступа к документу");
        }

        storageService.deleteFile(doc.getStorageKey());
        documentRepository.deleteById(documentId);
        log.info("Документ {} удалён пользователем {}", documentId, requesterId);
    }
}
