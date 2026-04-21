package ru.normacontrol.infrastructure.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.normacontrol.application.usecase.CheckDocumentUseCase;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka-консюмер: обрабатывает очередь проверок документов.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentCheckConsumer {

    private final CheckDocumentUseCase checkDocumentUseCase;

    @KafkaListener(
            topics = "${kafka.topic.document-check}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onDocumentCheckRequest(Map<String, String> message) {
        String documentIdStr = message.get("documentId");
        String userIdStr = message.get("userId");

        log.info("Получен запрос на проверку документа: {}", documentIdStr);

        try {
            UUID documentId = UUID.fromString(documentIdStr);
            UUID userId = UUID.fromString(userIdStr);

            checkDocumentUseCase.executeCheck(documentId, userId);
            log.info("Документ {} успешно проверен", documentId);

        } catch (Exception e) {
            log.error("Ошибка при обработке проверки документа {}: {}",
                    documentIdStr, e.getMessage(), e);
        }
    }
}
