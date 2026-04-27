package ru.normacontrol.domain.event;

import java.util.UUID;

/**
 * Event raised when a document is uploaded into the system.
 *
 * @param docId uploaded document identifier
 * @param userId uploader identifier
 * @param fileName original file name
 */
public record DocumentUploadedEvent(UUID docId, UUID userId, String fileName) {
}
