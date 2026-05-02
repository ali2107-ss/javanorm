package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.normacontrol.application.dto.response.UserResponse;
import ru.normacontrol.application.usecase.UserManagementUseCase;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Администрирование", description = "Управление пользователями")
public class AdminController {

    private final UserManagementUseCase userManagementUseCase;

    @PatchMapping("/users/{userId}/toggle")
    @Operation(summary = "Заблокировать / разблокировать пользователя")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> toggleUser(
            @PathVariable UUID userId,
            @RequestBody Map<String, Boolean> body) {
        try {
            boolean enabled = body.getOrDefault("enabled", true);
            UserResponse response = userManagementUseCase.toggleEnabled(userId, enabled);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Не удалось изменить статус пользователя"));
        }
    }
}
