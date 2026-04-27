package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.normacontrol.application.dto.DocumentComparisonDto;
import ru.normacontrol.application.usecase.CompareDocumentsUseCase;
import ru.normacontrol.presentation.dto.request.CompareRequest;

import java.util.UUID;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Сравнение документов", description = "API для сравнения двух версий документа по ГОСТ 19.201-78")
public class CompareController {

    private final CompareDocumentsUseCase compareDocumentsUseCase;

    @Operation(summary = "Сравнить две версии документа", 
               description = "Анализирует две версии документа на соответствие ГОСТ и возвращает diff нарушений")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Сравнение успешно выполнено",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DocumentComparisonDto.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные запроса", content = @Content),
            @ApiResponse(responseCode = "401", description = "Не авторизован", content = @Content),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа", content = @Content),
            @ApiResponse(responseCode = "404", description = "Один из документов не найден", content = @Content)
    })
    @PostMapping("/compare")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<DocumentComparisonDto> compare(
            @Valid @RequestBody CompareRequest request,
            Principal principal) {
            
        UUID userId = null;
        if (principal != null) {
            try {
                userId = UUID.fromString(principal.getName());
            } catch (IllegalArgumentException e) {
                // Если имя не UUID, генерируем случайный (для тестов) или логируем
                userId = UUID.randomUUID();
            }
        } else {
            userId = UUID.randomUUID();
        }

        DocumentComparisonDto result = compareDocumentsUseCase.compare(
                request.documentV1Id(), 
                request.documentV2Id(), 
                userId);
                
        return ResponseEntity.ok(result);
    }
}
