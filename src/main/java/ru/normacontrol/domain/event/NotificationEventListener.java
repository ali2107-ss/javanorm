package ru.normacontrol.domain.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import ru.normacontrol.infrastructure.websocket.ProgressPublisher;

/**
 * Sends user-facing notifications for domain events.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final ProgressPublisher progressPublisher;

    /**
     * Notify the client that the document was uploaded.
     *
     * @param event uploaded event
     */
    @Async
    @EventListener
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        progressPublisher.publishProgress(event.docId(), 0, "UPLOADED",
                "Документ " + event.fileName() + " загружен");
    }

    /**
     * Notify the client that the check started.
     *
     * @param event started event
     */
    @Async
    @EventListener
    public void onCheckStarted(CheckStartedEvent event) {
        progressPublisher.publishProgress(event.docId(), 5, "STARTED",
                "Проверка запущена: " + event.ruleSetName());
    }

    /**
     * Notify the client that the check finished.
     *
     * @param event completed event
     */
    @Async
    @EventListener
    public void onCheckCompleted(CheckCompletedEvent event) {
        progressPublisher.publishProgress(event.docId(), 100, "COMPLETED",
                "Проверка завершена. Балл: " + event.score());
    }
}
