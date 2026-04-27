package ru.normacontrol.presentation.controller;

import jakarta.persistence.EntityNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.normacontrol.application.dto.response.CheckResultResponse;
import ru.normacontrol.application.usecase.CheckDocumentUseCase;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.application.mapper.CheckResultMapper;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST-контроллер для результатов проверки.
 */
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
    @Operation(summary = "Получить последний результат проверки документа")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<CheckResultResponse> getLatestResult(@PathVariable UUID documentId) {
        CheckResultResponse response = checkDocumentUseCase.getLatestResult(documentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/document/{documentId}/history")
    @Operation(summary = "Получить историю всех проверок документа")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    public ResponseEntity<List<CheckResultResponse>> getHistory(@PathVariable UUID documentId) {
        List<CheckResult> results = checkResultRepository.findByDocumentId(documentId);
        List<CheckResultResponse> responses = results.stream()
                .map(checkResultMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{resultId}")
    @Operation(summary = "Получить результат проверки по ID")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<CheckResultResponse> getById(@PathVariable UUID resultId) {
        CheckResult result = checkResultRepository.findById(resultId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Результат проверки не найден: " + resultId));
        return ResponseEntity.ok(checkResultMapper.toResponse(result));
    }
}
