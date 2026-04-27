package ru.normacontrol.domain.repository;

import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.enums.DocumentStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-side port for document queries.
 */
public interface ReadDocumentRepository {

    /**
     * Find document by identifier.
     *
     * @param id document identifier
     * @return matching document when present
     */
    Optional<Document> findById(UUID id);

    /**
     * Find documents by owner.
     *
     * @param ownerId owner identifier
     * @return owner documents
     */
    List<Document> findByOwnerId(UUID ownerId);

    /**
     * Find documents by status.
     *
     * @param status document status
     * @return matching documents
     */
    List<Document> findByStatus(DocumentStatus status);
}
