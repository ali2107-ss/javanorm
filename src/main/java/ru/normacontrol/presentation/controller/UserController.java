package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.normacontrol.application.dto.response.UserResponse;
import ru.normacontrol.application.usecase.UserManagementUseCase;

import java.util.UUID;

/**
 * REST-контроллер для управления профилем пользователя.
 */
@RestController
@RequestMapping({"/users", "/api/v1/users"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Пользователи", description = "Управление профилем пользователя")
public class UserController {

    private final UserManagementUseCase userManagementUseCase;

    @GetMapping("/me")
    @Operation(summary = "Получить свой профиль")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getProfile(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        UserResponse response = userManagementUseCase.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Получить профиль пользователя по ID (ADMIN/REVIEWER)")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) {
        UserResponse response = userManagementUseCase.getProfile(userId);
        return ResponseEntity.ok(response);
    }
}
