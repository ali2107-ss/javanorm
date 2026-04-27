package ru.normacontrol.domain.event;

import java.util.UUID;

/**
 * Event raised when a document check is completed.
 *
 * @param docId checked document identifier
 * @param score resulting score from 0 to 100
 * @param passed whether the document passed the check
 */
public record CheckCompletedEvent(UUID docId, int score, boolean passed) {
}
