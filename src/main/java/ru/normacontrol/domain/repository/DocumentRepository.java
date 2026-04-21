package ru.normacontrol.domain.repository;

import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.enums.DocumentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Порт репозитория документов (Domain layer).
 */
public interface DocumentRepository {
    Document save(Document document);
    Optional<Document> findById(UUID id);
    List<Document> findByOwnerId(UUID ownerId);
    List<Document> findByStatus(DocumentStatus status);
    void deleteById(UUID id);
}
