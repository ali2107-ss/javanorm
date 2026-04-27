package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.normacontrol.application.dto.response.DocumentResponse;
import ru.normacontrol.application.usecase.DocumentUseCase;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for document operations.
 */
@RestController
@RequestMapping({"/documents", "/api/v1/documents"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Документы", description = "Загрузка, просмотр и удаление документов")
public class DocumentController {

    private final DocumentUseCase documentUseCase;

    /**
     * Upload a document for automatic checking.
     *
     * @param file uploaded file
     * @param authentication current security principal
     * @return created document DTO
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузить документ и поставить на проверку ГОСТ 19.201-78")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<DocumentResponse> upload(
            @Parameter(description = "Файл документа (PDF, DOCX, TXT или MD)")
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        DocumentResponse response = documentUseCase.execute(file, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get documents owned by current user.
     *
     * @param authentication current security principal
     * @return list of document DTOs
     */
    @GetMapping
    @Operation(summary = "Получить список своих документов")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<List<DocumentResponse>> getMyDocuments(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(documentUseCase.getByOwner(userId));
    }

    /**
     * Get a document by identifier.
     *
     * @param documentId document identifier
     * @param authentication current security principal
     * @return document DTO
     */
    @GetMapping("/{documentId}")
    @Operation(summary = "Получить документ по ID")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<DocumentResponse> getById(@PathVariable UUID documentId, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(documentUseCase.getById(documentId, userId));
    }

    /**
     * Download the latest PDF report for a document.
     *
     * @param documentId document identifier
     * @param authentication current security principal
     * @return PDF bytes
     */
    @GetMapping("/{documentId}/report")
    @Operation(summary = "Скачать PDF-отчёт по документу")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<byte[]> downloadReport(@PathVariable UUID documentId, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        byte[] payload = documentUseCase.downloadLatestReport(documentId, userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("report_" + documentId + ".pdf")
                        .build()
                        .toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(payload);
    }

    /**
     * Soft-delete a document.
     *
     * @param documentId document identifier
     * @param authentication current security principal
     * @return empty response
     */
    @DeleteMapping("/{documentId}")
    @Operation(summary = "Удалить документ")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID documentId, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        documentUseCase.delete(documentId, userId);
        return ResponseEntity.noContent().build();
    }
}
