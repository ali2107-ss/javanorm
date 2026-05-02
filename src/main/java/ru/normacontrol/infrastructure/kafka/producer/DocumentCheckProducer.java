package ru.normacontrol.infrastructure.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentCheckProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topic.document-check}")
    private String topic;

    public void sendCheckRequest(UUID documentId, UUID userId) {
        String message = String.format(
                "{\"documentId\":\"%s\",\"userId\":\"%s\"}",
                documentId,
                userId
        );

        kafkaTemplate.send(topic, documentId.toString(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Ошибка отправки в Kafka для документа {}: {}", documentId, ex.getMessage());
                    } else {
                        log.info("Документ {} отправлен в очередь проверки. Offset: {}",
                                documentId, result.getRecordMetadata().offset());
                    }
                });
    }
}
