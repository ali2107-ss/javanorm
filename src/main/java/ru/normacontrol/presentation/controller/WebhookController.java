package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.infrastructure.webhook.DocumentWebhookJpaEntity;
import ru.normacontrol.infrastructure.webhook.DocumentWebhookRepository;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents/{documentId}/webhook")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Webhook", description = "Webhook-уведомления о завершении проверки")
public class WebhookController {

    private final ReadDocumentRepository readDocumentRepository;
    private final DocumentWebhookRepository webhookRepository;

    @GetMapping
    @Operation(summary = "Получить webhook документа")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<?> get(@PathVariable UUID documentId, Authentication authentication) {
        requireOwner(documentId, UUID.fromString(authentication.getName()));
        return webhookRepository.findById(documentId)
                .<ResponseEntity<?>>map(webhook -> ResponseEntity.ok(Map.of(
                        "documentId", webhook.getDocumentId(),
                        "callbackUrl", webhook.getCallbackUrl()
                )))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping
    @Operation(summary = "Задать webhook URL для документа")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<?> upsert(@PathVariable UUID documentId,
                                    @RequestBody WebhookRequest request,
                                    Authentication authentication) {
        UUID ownerId = UUID.fromString(authentication.getName());
        requireOwner(documentId, ownerId);
        validateUrl(request.callbackUrl());
        DocumentWebhookJpaEntity existing = webhookRepository.findById(documentId).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        DocumentWebhookJpaEntity webhook = DocumentWebhookJpaEntity.builder()
                .documentId(documentId)
                .ownerId(ownerId)
                .callbackUrl(request.callbackUrl())
                .createdAt(existing != null ? existing.getCreatedAt() : now)
                .updatedAt(now)
                .build();
        webhookRepository.save(webhook);
        return ResponseEntity.ok(Map.of("documentId", documentId, "callbackUrl", webhook.getCallbackUrl()));
    }

    @DeleteMapping
    @Operation(summary = "Удалить webhook документа")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID documentId, Authentication authentication) {
        requireOwner(documentId, UUID.fromString(authentication.getName()));
        webhookRepository.deleteById(documentId);
        return ResponseEntity.noContent().build();
    }

    private void requireOwner(UUID documentId, UUID ownerId) {
        var document = readDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Документ не найден: " + documentId));
        if (!document.getOwnerId().equals(ownerId)) {
            throw new SecurityException("Нет доступа к документу");
        }
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("callbackUrl обязателен");
        }
        URI uri = URI.create(url);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Webhook URL должен начинаться с http:// или https://");
        }
    }

    public record WebhookRequest(String callbackUrl) {
    }
}
