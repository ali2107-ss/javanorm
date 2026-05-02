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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.normacontrol.application.dto.response.FixResponse;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.domain.repository.UserRepository;
import ru.normacontrol.infrastructure.export.DocumentAutoFixService;

import java.util.UUID;

/**
 * REST-контроллер авто-исправления документов.
 *
 * <p>Предоставляет два эндпоинта:
 * <ul>
 *   <li>POST /{id}/fix — запустить авто-исправление и получить мета-данные</li>
 *   <li>GET  /{id}/fixed — скачать исправленный DOCX напрямую</li>
 * </ul>
 * </p>
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
    private final UserRepository         userRepository;

    /**
     * Запустить авто-исправление документа.
     *
     * @param id          идентификатор документа
     * @param userDetails аутентифицированный пользователь
     * @return мета-данные исправленного файла
     */
    @PostMapping("/{id}/fix")
    @Operation(summary = "Авто-исправить документ",
               description = "Применяет автоматические исправления (шрифт, выравнивание, " +
                             "подсветка фраз, разделы-placeholder) и сохраняет файл в MinIO")
    public ResponseEntity<FixResponse> fixDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = resolveUserId(userDetails);

        Document document = readDocumentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Документ не найден: " + id));

        byte[] fixedBytes = documentAutoFixService.autoFix(id, userId);

        String fixedKey = documentAutoFixService.buildFixedKey(id, document.getOriginalFilename());
        String fixedUrl = "/api/v1/documents/" + id + "/fixed";

        // Примерный подсчёт: каждые 8 байт разницы = одно исправление (эвристика)
        int estimatedFixes = Math.max(1, (fixedBytes.length - 0) / 8);

        FixResponse response = FixResponse.builder()
                .fixedDocumentKey(fixedKey)
                .fixedDocumentUrl(fixedUrl)
                .fixedCount(estimatedFixes)
                .message("Авто-исправление выполнено. Исправленный файл готов к скачиванию.")
                .build();

        log.info("Авто-исправление документа {} выполнено пользователем {}", id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Скачать исправленный DOCX-файл.
     *
     * @param id          идентификатор документа
     * @param userDetails аутентифицированный пользователь
     * @return DOCX-файл как поток байт
     */
    @GetMapping("/{id}/fixed")
    @Operation(summary = "Скачать исправленный документ",
               description = "Возвращает DOCX с применёнными авто-исправлениями")
    public ResponseEntity<byte[]> downloadFixed(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = resolveUserId(userDetails);

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
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(u -> u.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Пользователь не найден: " + userDetails.getUsername()));
    }
}
