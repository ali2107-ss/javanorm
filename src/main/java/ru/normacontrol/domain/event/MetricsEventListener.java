package ru.normacontrol.domain.event;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Updates Micrometer metrics for domain events.
 */
@Component
@RequiredArgsConstructor
public class MetricsEventListener {

    private final MeterRegistry meterRegistry;

    /**
     * Count uploaded documents.
     *
     * @param event uploaded event
     */
    @EventListener
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        meterRegistry.counter("documents.uploaded.total").increment();
    }

    /**
     * Count started checks.
     *
     * @param event started event
     */
    @EventListener
    public void onCheckStarted(CheckStartedEvent event) {
        meterRegistry.counter("checks.started.total").increment();
    }

    /**
     * Count completed checks and score distribution.
     *
     * @param event completed event
     */
    @EventListener
    public void onCheckCompleted(CheckCompletedEvent event) {
        meterRegistry.counter("checks.completed.total").increment();
        meterRegistry.counter("checks.completed.status", "passed", Boolean.toString(event.passed())).increment();
        meterRegistry.summary("checks.score").record(event.score());
    }

    /**
     * Count deleted documents.
     *
     * @param event deleted event
     */
    @EventListener
    public void onDocumentDeleted(DocumentDeletedEvent event) {
        meterRegistry.counter("documents.deleted.total").increment();
    }
}
