package ru.normacontrol.domain.event;

import java.util.UUID;

/**
 * Event raised when a document is soft-deleted.
 *
 * @param docId deleted document identifier
 * @param userId user who deleted the document
 * @param fileName original file name
 */
public record DocumentDeletedEvent(UUID docId, UUID userId, String fileName) {
}
