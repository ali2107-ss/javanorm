package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.normacontrol.application.dto.response.CheckResultResponse;
import ru.normacontrol.application.mapper.CheckResultMapper;
import ru.normacontrol.application.usecase.CheckDocumentUseCase;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.repository.CheckResultRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/check-results", "/api/v1/check-results"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Результаты проверки", description = "Получение результатов проверки ГОСТ 19.201-78")
public class CheckResultController {

    private final CheckDocumentUseCase checkDocumentUseCase;
    private final CheckResultRepository checkResultRepository;
    private final CheckResultMapper checkResultMapper;

    @GetMapping("/document/{documentId}")
    @Transactional(readOnly = true)
    @Operation(summary = "Получить последний результат проверки документа")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<?> getLatestResult(@PathVariable UUID documentId) {
        try {
            CheckResultResponse response = checkDocumentUseCase.getLatestResult(documentId);
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            log.error("Ошибка: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Результат проверки не найден"));
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Не удалось получить результат проверки"));
        }
    }

    @GetMapping("/document/{documentId}/history")
    @Transactional(readOnly = true)
    @Operation(summary = "Получить историю проверок документа")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    public ResponseEntity<?> getHistory(@PathVariable UUID documentId) {
        try {
            List<CheckResultResponse> responses = checkResultRepository.findByDocumentId(documentId).stream()
                    .map(checkResultMapper::toResponse)
                    .toList();
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/{resultId}")
    @Transactional(readOnly = true)
    @Operation(summary = "Получить результат проверки по ID")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<?> getById(@PathVariable UUID resultId) {
        try {
            CheckResult result = checkResultRepository.findById(resultId)
                    .orElseThrow(() -> new EntityNotFoundException("Результат проверки не найден: " + resultId));
            return ResponseEntity.ok(checkResultMapper.toResponse(result));
        } catch (EntityNotFoundException e) {
            log.error("Ошибка: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Результат проверки не найден"));
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Не удалось получить результат проверки"));
        }
    }
}
