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
import org.springframework.http.ContentDisposition;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.normacontrol.application.dto.response.FixResponse;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.infrastructure.export.DocumentAutoFixService;
import ru.normacontrol.infrastructure.minio.MinioStorageService;

import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Авто-исправление", description = "Автоматическое исправление нарушений ГОСТ в DOCX")
public class FixController {

    private final DocumentAutoFixService documentAutoFixService;
    private final ReadDocumentRepository readDocumentRepository;
    private final MinioStorageService storageService;

    @PostMapping("/{id}/fix")
    @Operation(summary = "Авто-исправить документ")
    public ResponseEntity<?> fixDocument(@PathVariable UUID id, Authentication authentication) {
        try {
            UUID userId = UUID.fromString(authentication.getName());
            readDocumentRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Документ не найден: " + id));

            DocumentAutoFixService.AutoFixResult fixed = documentAutoFixService.autoFixWithResult(id, userId);
            FixResponse response = FixResponse.builder()
                    .fixedDocumentKey(fixed.fixedKey())
                    .fixedDocumentUrl("/api/v1/documents/" + id + "/fixed")
                    .fixedCount(fixed.fixedCount())
                    .manualActions(fixed.manualActions())
                    .message("Авто-исправление выполнено. Исправленный файл готов к скачиванию.")
                    .build();

            log.info("Auto-fix completed for document {} by user {}", id, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Auto-fix failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "AUTO_FIX_FAILED",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/{id}/fixed")
    @Operation(summary = "Скачать исправленный документ")
    public ResponseEntity<?> downloadFixed(@PathVariable UUID id, Authentication authentication) {
        try {
            UUID.fromString(authentication.getName());
            Document document = readDocumentRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Документ не найден: " + id));

            String fixedKey = documentAutoFixService.buildFixedKey(id, document.getOriginalFilename());
            byte[] fixedBytes = storageService.downloadBytes(fixedKey);
            String filename = "fixed_" + stripExtension(document.getOriginalFilename()) + ".docx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(filename, StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .contentLength(fixedBytes.length)
                    .body(fixedBytes);
        } catch (Exception e) {
            log.error("Fixed document download failed: {}", e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    private String stripExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "document";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot > 0 ? originalFilename.substring(0, dot) : originalFilename;
    }
}
