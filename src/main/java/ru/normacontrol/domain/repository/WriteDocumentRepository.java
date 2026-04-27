package ru.normacontrol.domain.repository;

import ru.normacontrol.domain.entity.Document;

import java.util.UUID;

/**
 * Write-side port for document state changes.
 */
public interface WriteDocumentRepository {

    /**
     * Persist a document aggregate.
     *
     * @param document document to save
     * @return saved aggregate
     */
    Document save(Document document);

    /**
     * Delete a document by identifier.
     *
     * @param id document identifier
     */
    void deleteById(UUID id);
}
