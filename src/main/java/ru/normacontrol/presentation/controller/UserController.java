package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.normacontrol.application.dto.request.ChangePasswordRequest;
import ru.normacontrol.application.dto.request.UpdateProfileRequest;
import ru.normacontrol.application.dto.response.ProfileStatsResponse;
import ru.normacontrol.application.dto.response.UserResponse;
import ru.normacontrol.application.usecase.UserManagementUseCase;
import ru.normacontrol.infrastructure.persistence.repository.CheckResultJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.DocumentJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.UserJpaRepository;

import java.time.LocalDate;
import java.util.Map;
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
    private final DocumentJpaRepository documentJpaRepository;
    private final CheckResultJpaRepository checkResultJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @GetMapping("/me")
    @Operation(summary = "Получить свой профиль")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getProfile(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        UserResponse response = userManagementUseCase.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> updateProfile(Authentication authentication,
                                                      @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(userManagementUseCase.updateProfile(userId, request));
    }

    @GetMapping("/me/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileStatsResponse> getMyStats(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        long documents = documentJpaRepository.countByOwner_IdAndDeletedFalse(userId);
        long checks = checkResultJpaRepository.countByDocument_Owner_Id(userId);
        long checksToday = checkResultJpaRepository.countByDocument_Owner_IdAndCheckedAtAfter(
                userId, LocalDate.now().atStartOfDay());
        long passed = checkResultJpaRepository.countByDocument_Owner_IdAndComplianceScoreGreaterThanEqual(userId, 80);
        long failed = checkResultJpaRepository.countByDocument_Owner_IdAndComplianceScoreLessThan(userId, 80);
        double average = checkResultJpaRepository.findAverageComplianceScoreByOwner(userId);
        return ResponseEntity.ok(new ProfileStatsResponse(
                checks,
                documents,
                checksToday,
                passed,
                failed,
                Math.round(average * 10.0) / 10.0
        ));
    }

    @GetMapping("/me/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> exportMyPersonalData(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        UserResponse profile = userManagementUseCase.getProfile(userId);
        long documents = documentJpaRepository.countByOwner_IdAndDeletedFalse(userId);
        long checks = checkResultJpaRepository.countByDocument_Owner_Id(userId);
        var user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return ResponseEntity.ok(Map.of(
                "profile", profile,
                "documentsCount", documents,
                "checksCount", checks,
                "notificationSettings", Map.of(
                        "emailReportsEnabled", user.isEmailReportsEnabled(),
                        "gostUpdatesEnabled", user.isGostUpdatesEnabled()
                )
        ));
    }

    @PatchMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userManagementUseCase.changePassword(UUID.fromString(authentication.getName()), request);
    }

    @GetMapping("/me/notification-settings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Boolean>> getNotificationSettings(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        var user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));
        return ResponseEntity.ok(Map.of(
                "emailReportsEnabled", user.isEmailReportsEnabled(),
                "gostUpdatesEnabled", user.isGostUpdatesEnabled()
        ));
    }

    @PatchMapping("/me/notification-settings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Boolean>> updateNotificationSettings(
            Authentication authentication,
            @RequestBody Map<String, Boolean> body) {
        UUID userId = UUID.fromString(authentication.getName());
        var user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));
        user.setEmailReportsEnabled(body.getOrDefault("emailReportsEnabled", user.isEmailReportsEnabled()));
        user.setGostUpdatesEnabled(body.getOrDefault("gostUpdatesEnabled", user.isGostUpdatesEnabled()));
        userJpaRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "emailReportsEnabled", user.isEmailReportsEnabled(),
                "gostUpdatesEnabled", user.isGostUpdatesEnabled()
        ));
    }

    @DeleteMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyAccount(Authentication authentication) {
        userManagementUseCase.anonymizeAccount(UUID.fromString(authentication.getName()));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Получить профиль пользователя по ID (ADMIN/REVIEWER)")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) {
        UserResponse response = userManagementUseCase.getProfile(userId);
        return ResponseEntity.ok(response);
    }
}
