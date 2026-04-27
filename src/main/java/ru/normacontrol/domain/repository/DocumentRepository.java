package ru.normacontrol.domain.repository;

/**
 * Backward-compatible combined document repository port.
 */
public interface DocumentRepository extends ReadDocumentRepository, WriteDocumentRepository {
}
