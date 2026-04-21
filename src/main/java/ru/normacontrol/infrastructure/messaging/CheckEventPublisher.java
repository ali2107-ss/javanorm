package ru.normacontrol.infrastructure.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publisher для публикации событий в Kafka.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public void publishCheckRequested(UUID documentId, UUID userId) {
        Map<String, Object> payload = Map.of(
                "documentId", documentId,
                "userId", userId,
                "timestamp", System.currentTimeMillis()
        );
        kafkaTemplate.send("normacontrol.check.requested", documentId.toString(), payload);
        
        meterRegistry.counter("kafka.events.published", "event", "requested").increment();
        log.info("Опубликовано событие проверки документа {}", documentId);
    }

    public void publishCheckCompleted(UUID documentId, int score, boolean passed, List<String> violationCodes) {
        Map<String, Object> payload = Map.of(
                "documentId", documentId,
                "score", score,
                "passed", passed,
                "violationCodes", violationCodes,
                "timestamp", System.currentTimeMillis()
        );
        kafkaTemplate.send("normacontrol.check.completed", documentId.toString(), payload);
        
        meterRegistry.counter("kafka.events.published", "event", "completed").increment();
        log.info("Опубликовано событие завершения проверки документа {}", documentId);
    }
}
