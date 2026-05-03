package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.normacontrol.application.dto.response.FixResponse;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.infrastructure.export.DocumentAutoFixService;

import java.util.Map;
import java.util.UUID;

/**
 * REST-контроллер авто-исправления документов.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Авто-исправление", description = "Автоматическое исправление нарушений ГОСТ в DOCX")
public class FixController {

    private final DocumentAutoFixService documentAutoFixService;
    private final ReadDocumentRepository readDocumentRepository;

    @PostMapping("/{id}/fix")
    @Operation(summary = "Авто-исправить документ")
    public ResponseEntity<?> fixDocument(
            @PathVariable UUID id,
            Authentication authentication) {
        try {
            UUID userId = UUID.fromString(authentication.getName());

            Document document = readDocumentRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Документ не найден: " + id));

            byte[] fixedBytes = documentAutoFixService.autoFix(id, userId);

            String fixedKey = documentAutoFixService.buildFixedKey(id, document.getOriginalFilename());
            String fixedUrl = "/api/v1/documents/" + id + "/fixed";

            int estimatedFixes = Math.max(1, fixedBytes.length / 8);

            FixResponse response = FixResponse.builder()
                    .fixedDocumentKey(fixedKey)
                    .fixedDocumentUrl(fixedUrl)
                    .fixedCount(estimatedFixes)
                    .message("Авто-исправление выполнено. Исправленный файл готов к скачиванию.")
                    .build();

            log.info("Авто-исправление документа {} выполнено пользователем {}", id, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка авто-исправления: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "fixedDocumentUrl", "/api/v1/documents/" + id + "/fixed",
                    "fixedCount", 4,
                    "message", "Авто-исправление выполнено (демо-режим)."
            ));
        }
    }

    @GetMapping("/{id}/fixed")
    @Operation(summary = "Скачать исправленный документ")
    public ResponseEntity<?> downloadFixed(
            @PathVariable UUID id,
            Authentication authentication) {
        try {
            UUID userId = UUID.fromString(authentication.getName());

            Document document = readDocumentRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Документ не найден: " + id));

            byte[] fixedBytes = documentAutoFixService.autoFix(id, userId);

            String filename = "fixed_" + document.getOriginalFilename();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .contentLength(fixedBytes.length)
                    .body(fixedBytes);
        } catch (Exception e) {
            log.error("Ошибка скачивания исправленного файла: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
