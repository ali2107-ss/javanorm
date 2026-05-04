package ru.normacontrol.infrastructure.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import ru.normacontrol.domain.entity.CheckResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookNotificationService {

    private final DocumentWebhookRepository repository;
    private final WebClient.Builder webClientBuilder;

    @Async
    public void notifyCompleted(UUID documentId, CheckResult result) {
        repository.findById(documentId).ifPresent(webhook -> {
            Map<String, Object> payload = Map.of(
                    "event", "check.completed",
                    "documentId", documentId,
                    "resultId", result.getId(),
                    "passed", result.isPassed(),
                    "complianceScore", result.getComplianceScore(),
                    "totalViolations", result.getViolations().size(),
                    "checkedAt", result.getCheckedAt()
            );
            post(webhook.getCallbackUrl(), payload);
        });
    }

    @Async
    public void notifyFailed(UUID documentId, String message) {
        repository.findById(documentId).ifPresent(webhook -> {
            Map<String, Object> payload = Map.of(
                    "event", "check.failed",
                    "documentId", documentId,
                    "message", message,
                    "failedAt", LocalDateTime.now()
            );
            post(webhook.getCallbackUrl(), payload);
        });
    }

    private void post(String url, Map<String, Object> payload) {
        try {
            webClientBuilder.build()
                    .post()
                    .uri(url)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Webhook delivered to {}", url);
        } catch (Exception e) {
            log.warn("Webhook delivery failed to {}: {}", url, e.getMessage());
        }
    }
}
