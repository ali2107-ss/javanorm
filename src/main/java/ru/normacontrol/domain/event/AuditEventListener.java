package ru.normacontrol.domain.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import ru.normacontrol.application.service.AuditService;

/**
 * Persists audit trail records for domain events.
 */
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditService auditService;

    /**
     * Persist document upload audit entry.
     *
     * @param event uploaded event
     */
    @Async
    @EventListener
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        auditService.log(event.userId(), null, "DOCUMENT_UPLOADED", "DOCUMENT", event.docId(),
                "{\"fileName\":\"" + event.fileName() + "\"}", true, null);
    }

    /**
     * Persist check start audit entry.
     *
     * @param event started event
     */
    @Async
    @EventListener
    public void onCheckStarted(CheckStartedEvent event) {
        auditService.log(null, null, "CHECK_STARTED", "DOCUMENT", event.docId(),
                "{\"ruleSetName\":\"" + event.ruleSetName() + "\"}", true, null);
    }

    /**
     * Persist check completion audit entry.
     *
     * @param event completed event
     */
    @Async
    @EventListener
    public void onCheckCompleted(CheckCompletedEvent event) {
        auditService.log(null, null, "CHECK_COMPLETED", "DOCUMENT", event.docId(),
                "{\"score\":" + event.score() + ",\"passed\":" + event.passed() + "}", true, null);
    }

    /**
     * Persist document deletion audit entry.
     *
     * @param event deleted event
     */
    @Async
    @EventListener
    public void onDocumentDeleted(DocumentDeletedEvent event) {
        auditService.log(event.userId(), null, "DOCUMENT_DELETED", "DOCUMENT", event.docId(),
                "{\"fileName\":\"" + event.fileName() + "\"}", true, null);
    }
}
