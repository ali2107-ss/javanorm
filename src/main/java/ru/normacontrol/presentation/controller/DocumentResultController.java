package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
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
import ru.normacontrol.application.usecase.CheckDocumentUseCase;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentResultController {

    private final CheckDocumentUseCase checkDocumentUseCase;

    @GetMapping("/{documentId}/result")
    @Transactional(readOnly = true)
    @Operation(summary = "Получить результат проверки документа")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<?> getDocumentResult(@PathVariable UUID documentId) {
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
}
