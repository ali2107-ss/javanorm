package ru.normacontrol.domain.event;

import java.util.UUID;

/**
 * Event raised when a document check starts.
 *
 * @param docId checked document identifier
 * @param ruleSetName rule set name used for validation
 */
public record CheckStartedEvent(UUID docId, String ruleSetName) {
}
