package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
 * REST-контроллер для работы с документами.
 */
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Документы", description = "Загрузка, просмотр и удаление документов")
public class DocumentController {

    private final DocumentUseCase documentUseCase;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузить документ и поставить на проверку ГОСТ 19.201-78")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<DocumentResponse> upload(
            @Parameter(description = "Файл документа (PDF или DOCX)")
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        DocumentResponse response = documentUseCase.uploadAndCheck(file, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Получить список своих документов")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<List<DocumentResponse>> getMyDocuments(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        List<DocumentResponse> documents = documentUseCase.getByOwner(userId);
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Получить документ по ID")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<DocumentResponse> getById(
            @PathVariable UUID documentId,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        DocumentResponse response = documentUseCase.getById(documentId, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{documentId}")
    @Operation(summary = "Удалить документ")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID documentId,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        documentUseCase.delete(documentId, userId);
        return ResponseEntity.noContent().build();
    }
}
