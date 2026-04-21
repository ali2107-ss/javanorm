package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.normacontrol.application.dto.response.UserResponse;
import ru.normacontrol.application.usecase.UserManagementUseCase;

import java.util.Map;
import java.util.UUID;

/**
 * REST-контроллер администрирования.
 * Доступен только для ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Администрирование", description = "Управление пользователями (ADMIN)")
public class AdminController {

    private final UserManagementUseCase userManagementUseCase;

    @PatchMapping("/users/{userId}/toggle")
    @Operation(summary = "Заблокировать / разблокировать пользователя")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> toggleUser(
            @PathVariable UUID userId,
            @RequestBody Map<String, Boolean> body) {

        boolean enabled = body.getOrDefault("enabled", true);
        UserResponse response = userManagementUseCase.toggleEnabled(userId, enabled);
        return ResponseEntity.ok(response);
    }
}
