package ru.normacontrol.application.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.normacontrol.application.usecase.CheckDocumentUseCase;

/**
 * Sends check request to Kafka only after a successful transaction commit.
 */
@Component
@RequiredArgsConstructor
public class DocumentCheckRequestedEventListener {

    private final CheckDocumentUseCase checkDocumentUseCase;

    /**
     * Send Kafka message after commit.
     *
     * @param event check request event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentCheckRequested(DocumentCheckRequestedEvent event) {
        checkDocumentUseCase.executeCheck(event.documentId(), event.userId());
    }
}
