package ru.normacontrol.infrastructure.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka-продюсер: отправляет запрос на проверку документа в очередь.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentCheckProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.document-check}")
    private String topic;

    /**
     * Отправить запрос на проверку документа.
     */
    public void sendCheckRequest(UUID documentId, UUID userId) {
        Map<String, String> message = Map.of(
                "documentId", documentId.toString(),
                "userId", userId.toString()
        );

        kafkaTemplate.send(topic, documentId.toString(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Ошибка отправки в Kafka для документа {}: {}",
                                documentId, ex.getMessage());
                    } else {
                        log.info("Документ {} отправлен в очередь проверки. Offset: {}",
                                documentId, result.getRecordMetadata().offset());
                    }
                });
    }
}
