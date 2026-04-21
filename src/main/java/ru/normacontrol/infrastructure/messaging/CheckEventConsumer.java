package ru.normacontrol.infrastructure.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import ru.normacontrol.application.usecase.CheckDocumentUseCase;

import java.util.Map;
import java.util.UUID;

/**
 * Consumer для обработки очереди документов на проверку ГОСТ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckEventConsumer {

    private final CheckDocumentUseCase checkDocumentUseCase;
    private final MeterRegistry meterRegistry;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 4000),
            autoCreateTopics = "false",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "normacontrol.check.requested", groupId = "norma-control-group")
    public void onCheckRequested(@Payload Map<String, Object> payload,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(name = "traceId", required = false) String traceId) {

        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put("traceId", traceId);

        UUID documentId = UUID.fromString(payload.get("documentId").toString());
        UUID userId = UUID.fromString(payload.get("userId").toString());

        log.info("Получено событие на проверку (Kafka): documentId={}", documentId);

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // Асинхронный вызов UseCase с ожиданием результата для корректной работы Retry в Kafka
            checkDocumentUseCase.executeCheck(documentId, userId).join();
        } finally {
            sample.stop(meterRegistry.timer("kafka.consumer.processing.time"));
            MDC.clear();
        }
    }

    @DltHandler
    public void processDltMessage(@Payload Map<String, Object> payload,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        UUID documentId = UUID.fromString(payload.get("documentId").toString());
        log.error("Событие проверки документа {} попало в DLT после 3 неудачных попыток. " +
                  "Требуется ручное вмешательство или отправка уведомления пользователю.", documentId);
    }
}
