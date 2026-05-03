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
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.infrastructure.minio.MinioStorageService;
import ru.normacontrol.infrastructure.report.ReportGenerator;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping({"/documents", "/api/v1/documents"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Документы", description = "Загрузка, просмотр и удаление документов")
public class DocumentController {

    private final DocumentUseCase documentUseCase;
    private final CheckDocumentUseCase checkDocumentUseCase;
    private final CheckResultRepository checkResultRepository;
    private final ReadDocumentRepository readDocumentRepository;
    private final MinioStorageService storageService;
    private final ReportGenerator reportGenerator;

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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "documentId", documentId,
                    "status", "FAILED",
                    "message", "Не удалось запустить проверку: " + e.getMessage()
            ));
        }
    }

    /**
     * Старый эндпоинт (обратная совместимость) — скачать PDF из MinIO.
     * При отсутствии файла в MinIO возвращает 404.
     */
    @GetMapping("/{documentId}/report")
    @Operation(summary = "Скачать PDF-отчёт (только из MinIO)")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<?> downloadReportLegacy(@PathVariable UUID documentId, Authentication authentication) {
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
            log.warn("Файл отчёта не найден в MinIO для документа {}: {}", documentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "PDF-отчёт ещё не готов"));
        }
    }

    /**
     * Новый эндпоинт с полным fallback:
     * 1. Берём последний CheckResult документа.
     * 2. Пробуем скачать готовый PDF из MinIO.
     * 3. Если MinIO недоступен — генерируем PDF прямо в памяти через iText 7.
     * 4. Возвращаем байты с заголовком Content-Disposition: attachment.
     */
    @GetMapping("/{documentId}/report/download")
    @Operation(summary = "Скачать PDF-отчёт (с автоматической генерацией)")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<byte[]> downloadReport(
            @PathVariable UUID documentId,
            Authentication authentication) {
        try {
            UUID userId = UUID.fromString(authentication.getName());

            // Находим последний результат проверки
            CheckResult result = checkResultRepository.findLatestByDocumentId(documentId)
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                            "Результат проверки не найден: " + documentId));

            // Находим исходный документ (для метаданных в PDF)
            Document sourceDocument = readDocumentRepository.findById(documentId).orElse(null);

            byte[] pdfBytes = reportGenerator.generatePdfBytes(result, sourceDocument);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename(buildReportFilename(sourceDocument, documentId), StandardCharsets.UTF_8)
                                    .build()
                                    .toString())
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);

        } catch (Exception e) {
            log.error("Ошибка генерации/скачивания отчёта для документа {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String buildReportFilename(Document sourceDocument, UUID documentId) {
        String baseName = sourceDocument != null ? sourceDocument.getOriginalFilename() : null;
        if (baseName == null || baseName.isBlank()) {
            baseName = "document-" + documentId;
        }
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }
        return baseName + "_report.pdf";
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
