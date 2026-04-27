package ru.normacontrol.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.enums.DocumentStatus;
import ru.normacontrol.domain.repository.DocumentRepository;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.domain.repository.WriteDocumentRepository;
import ru.normacontrol.infrastructure.persistence.entity.DocumentJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.DocumentJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter for both read and write document ports.
 */
@Component
@RequiredArgsConstructor
public class DocumentRepositoryAdapter implements DocumentRepository, ReadDocumentRepository, WriteDocumentRepository {

    private final DocumentJpaRepository jpaRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public Document save(Document document) {
        return toDomain(jpaRepository.save(toJpaEntity(document)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Document> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Document> findByOwnerId(UUID ownerId) {
        return jpaRepository.findByOwnerId(ownerId).stream().map(this::toDomain).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Document> findByStatus(DocumentStatus status) {
        return jpaRepository.findByStatus(status).stream().map(this::toDomain).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private DocumentJpaEntity toJpaEntity(Document doc) {
        return DocumentJpaEntity.builder()
                .id(doc.getId())
                .originalFilename(doc.getOriginalFilename())
                .storageKey(doc.getStorageKey())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .status(doc.getStatus())
                .ownerId(doc.getOwnerId())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private Document toDomain(DocumentJpaEntity entity) {
        return Document.builder()
                .id(entity.getId())
                .originalFilename(entity.getOriginalFilename())
                .storageKey(entity.getStorageKey())
                .contentType(entity.getContentType())
                .fileSize(entity.getFileSize())
                .status(entity.getStatus())
                .ownerId(entity.getOwnerId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
