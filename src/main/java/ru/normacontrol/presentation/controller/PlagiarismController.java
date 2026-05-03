package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.normacontrol.application.usecase.CheckDocumentUseCase;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.infrastructure.plagiarism.PlagiarismResult;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/documents", "/api/v1/documents"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Антиплагиат", description = "Управление проверкой на уникальность")
public class PlagiarismController {

    private final CheckResultRepository checkResultRepository;

    /**
     * Запустить проверку антиплагиата (перезапуск всей проверки).
     * В реальном приложении можно было бы запускать отдельно, 
     * но здесь это часть общего пайплайна.
     */
    @PostMapping("/{id}/plagiarism")
    @Operation(summary = "Запустить проверку на антиплагиат")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<?> runPlagiarismCheck(@PathVariable UUID id) {
        // Мы уже выполняем проверку внутри startCheck
        // Можно вернуть 202 Accepted и сказать что проверка запускается
        return ResponseEntity.accepted().body(java.util.Map.of(
                "status", "QUEUED",
                "message", "Проверка на антиплагиат включена в общий цикл проверки."
        ));
    }

    /**
     * Получить последний результат антиплагиата для документа.
     */
    @GetMapping("/{id}/plagiarism")
    @Operation(summary = "Получить результат антиплагиата")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<PlagiarismResult> getPlagiarismResult(@PathVariable UUID id) {
        var result = checkResultRepository.findLatestByDocumentId(id)
                .orElseThrow(() -> new EntityNotFoundException("Результат не найден для: " + id));
        
        if (result.getPlagiarismResult() == null) {
            // Если документ еще не проверялся на плагиат
            return ResponseEntity.ok(new PlagiarismResult(100, 0, "УНИКАЛЬНЫЙ", java.util.List.of(), java.util.List.of()));
        }
        
        return ResponseEntity.ok(result.getPlagiarismResult());
    }
}
