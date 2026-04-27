package ru.normacontrol.application.event;

import java.util.UUID;

/**
 * Application event published when a document check must be sent to Kafka after commit.
 *
 * @param documentId document identifier
 * @param userId user identifier
 */
public record DocumentCheckRequestedEvent(UUID documentId, UUID userId) {
}
