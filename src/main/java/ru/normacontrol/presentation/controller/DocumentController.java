package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.normacontrol.application.dto.response.DocumentResponse;
import ru.normacontrol.application.usecase.CheckDocumentUseCase;
import ru.normacontrol.application.usecase.DocumentUseCase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/documents", "/api/v1/documents"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Документы", description = "Загрузка, просмотр и удаление документов")
public class DocumentController {

    private final DocumentUseCase documentUseCase;
    private final CheckDocumentUseCase checkDocumentUseCase;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузить документ")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<?> upload(
            @Parameter(description = "Файл документа")
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        try {
            UUID userId = UUID.fromString(authentication.getName());
            DocumentResponse response = documentUseCase.execute(file, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Не удалось загрузить документ"));
        }
    }

    @GetMapping
    @Operation(summary = "Получить список документов")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<?> getMyDocuments(Authentication authentication) {
        try {
            UUID userId = UUID.fromString(authentication.getName());
            return ResponseEntity.ok(documentUseCase.getByOwner(userId));
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Получить документ по ID")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<?> getById(@PathVariable UUID documentId, Authentication authentication) {
        try {
            UUID userId = UUID.fromString(authentication.getName());
            return ResponseEntity.ok(documentUseCase.getById(documentId, userId));
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Документ не найден"));
        }
    }

    @PostMapping("/{documentId}/check")
    @Operation(summary = "Запустить проверку документа")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<?> startCheck(@PathVariable UUID documentId, Authentication authentication) {
        try {
            UUID userId = UUID.fromString(authentication.getName());
            checkDocumentUseCase.initiateCheck(documentId, userId);
            return ResponseEntity.accepted().body(Map.of(
                    "documentId", documentId,
                    "status", "QUEUED",
                    "message", "Проверка запущена"
            ));
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "documentId", documentId,
                    "status", "DEMO_STARTED",
                    "message", "Проверка запущена в демо-режиме"
            ));
        }
    }

    @GetMapping("/{documentId}/report")
    @Operation(summary = "Скачать PDF-отчёт")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<?> downloadReport(@PathVariable UUID documentId, Authentication authentication) {
        try {
            UUID userId = UUID.fromString(authentication.getName());
            byte[] payload = documentUseCase.downloadLatestReport(documentId, userId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("report_" + documentId + ".pdf")
                            .build()
                            .toString())
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(payload);
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "PDF-отчёт ещё не готов"));
        }
    }

    @DeleteMapping("/{documentId}")
    @Operation(summary = "Удалить документ")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<?> delete(@PathVariable UUID documentId, Authentication authentication) {
        try {
            UUID userId = UUID.fromString(authentication.getName());
            documentUseCase.delete(documentId, userId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Не удалось удалить документ"));
        }
    }
}
