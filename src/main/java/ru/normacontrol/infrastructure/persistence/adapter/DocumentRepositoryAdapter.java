package ru.normacontrol.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.enums.DocumentStatus;
import ru.normacontrol.domain.repository.DocumentRepository;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.domain.repository.WriteDocumentRepository;
import ru.normacontrol.infrastructure.persistence.entity.DocumentJpaEntity;
import ru.normacontrol.infrastructure.persistence.entity.UserJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.DocumentJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DocumentRepositoryAdapter implements DocumentRepository, ReadDocumentRepository, WriteDocumentRepository {

    private final DocumentJpaRepository jpaRepository;

    @Override
    public Document save(Document document) {
        return toDomain(jpaRepository.save(toJpaEntity(document)));
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return jpaRepository.findById(id)
                .filter(entity -> !entity.isDeleted())
                .map(this::toDomain);
    }

    @Override
    public List<Document> findByOwnerId(UUID ownerId) {
        return jpaRepository.findByOwner_IdAndDeletedFalse(ownerId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Document> findByStatus(DocumentStatus status) {
        return jpaRepository.findByStatusAndDeletedFalse(status).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private DocumentJpaEntity toJpaEntity(Document document) {
        return DocumentJpaEntity.builder()
                .id(document.getId())
                .originalFileName(document.getOriginalFilename())
                .storagePath(document.getStorageKey())
                .type(resolveType(document.getContentType(), document.getOriginalFilename()))
                .status(document.getStatus())
                .fileSizeBytes(document.getFileSize())
                .owner(UserJpaEntity.builder().id(document.getOwnerId()).build())
                .deleted(document.getStatus() == DocumentStatus.DELETED)
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .deletedAt(document.getStatus() == DocumentStatus.DELETED ? document.getUpdatedAt() : null)
                .build();
    }

    private Document toDomain(DocumentJpaEntity entity) {
        return Document.builder()
                .id(entity.getId())
                .originalFilename(entity.getOriginalFileName())
                .storageKey(entity.getStoragePath())
                .contentType(entity.getType())
                .fileSize(entity.getFileSizeBytes())
                .status(entity.getStatus())
                .ownerId(entity.getOwner().getId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String resolveType(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank()) {
            if (contentType.contains("pdf")) {
                return "PDF";
            }
            if (contentType.contains("word")) {
                return "DOCX";
            }
        }
        if (filename != null) {
            if (filename.toLowerCase().endsWith(".pdf")) {
                return "PDF";
            }
            if (filename.toLowerCase().endsWith(".docx")) {
                return "DOCX";
            }
        }
        return "TXT";
    }
}
