package ru.normacontrol.infrastructure.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.normacontrol.application.usecase.CheckDocumentUseCase;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.entity.User;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.domain.repository.UserRepository;
import ru.normacontrol.infrastructure.notification.EmailNotificationService;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka-консюмер: обрабатывает очередь проверок документов.
 *
 * <p>После успешной проверки отправляет email-уведомление
 * владельцу документа через {@link EmailNotificationService}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentCheckConsumer {

    private final CheckDocumentUseCase    checkDocumentUseCase;
    private final EmailNotificationService emailNotificationService;
    private final CheckResultRepository   checkResultRepository;
    private final ReadDocumentRepository  readDocumentRepository;
    private final UserRepository          userRepository;
    private final ObjectMapper            objectMapper;

    @KafkaListener(
            topics = "${kafka.topic.document-check}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onDocumentCheckRequest(String payload) {
        Map<String, String> message;
        try {
            message = objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Не удалось прочитать Kafka-сообщение проверки: {}", e.getMessage(), e);
            return;
        }

        String documentIdStr = message.get("documentId");
        String userIdStr     = message.get("userId");

        log.info("Получен запрос на проверку документа: {}", documentIdStr);

        try {
            UUID documentId = UUID.fromString(documentIdStr);
            UUID userId     = UUID.fromString(userIdStr);

            checkDocumentUseCase.executeCheck(documentId, userId);
            log.info("Документ {} успешно проверен", documentId);

            // Email-уведомление — выполняется асинхронно, не блокирует поток
            sendEmailNotification(documentId, userId);

        } catch (Exception e) {
            log.error("Ошибка при обработке проверки документа {}: {}",
                    documentIdStr, e.getMessage(), e);
        }
    }

    /**
     * Загрузить данные и отправить email владельцу документа.
     * Ошибки логируются, но не пробрасываются — email не должен
     * влиять на основной результат проверки.
     */
    private void sendEmailNotification(UUID documentId, UUID userId) {
        try {
            CheckResult result = checkResultRepository
                    .findLatestByDocumentId(documentId)
                    .orElse(null);
            if (result == null) return;

            Document document = readDocumentRepository
                    .findById(documentId)
                    .orElse(null);
            if (document == null) return;

            User user = userRepository
                    .findById(userId)
                    .orElse(null);
            if (user == null) return;

            emailNotificationService.sendCheckCompleted(user, result, document);

        } catch (Exception e) {
            log.warn("Не удалось отправить email-уведомление для документа {}: {}",
                    documentId, e.getMessage());
        }
    }
}
