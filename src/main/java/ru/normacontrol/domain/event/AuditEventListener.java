package ru.normacontrol.domain.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import ru.normacontrol.infrastructure.persistence.entity.AuditLogJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.AuditLogJpaRepository;

import java.time.LocalDateTime;

/**
 * Persists audit trail records for domain events.
 */
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogJpaRepository auditLogJpaRepository;

    /**
     * Persist document upload audit entry.
     *
     * @param event uploaded event
     */
    @Async
    @EventListener
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        save("DOCUMENT_UPLOADED", event.docId(), event.userId(), "File: " + event.fileName());
    }

    /**
     * Persist check start audit entry.
     *
     * @param event started event
     */
    @Async
    @EventListener
    public void onCheckStarted(CheckStartedEvent event) {
        save("CHECK_STARTED", event.docId(), null, "Rule set: " + event.ruleSetName());
    }

    /**
     * Persist check completion audit entry.
     *
     * @param event completed event
     */
    @Async
    @EventListener
    public void onCheckCompleted(CheckCompletedEvent event) {
        save("CHECK_COMPLETED", event.docId(), null,
                "Score: " + event.score() + ", passed: " + event.passed());
    }

    /**
     * Persist document deletion audit entry.
     *
     * @param event deleted event
     */
    @Async
    @EventListener
    public void onDocumentDeleted(DocumentDeletedEvent event) {
        save("DOCUMENT_DELETED", event.docId(), event.userId(), "File: " + event.fileName());
    }

    private void save(String action, java.util.UUID docId, java.util.UUID userId, String details) {
        auditLogJpaRepository.save(AuditLogJpaEntity.builder()
                .action(action)
                .documentId(docId)
                .userId(userId)
                .details(details)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
