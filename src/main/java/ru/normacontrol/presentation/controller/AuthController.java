package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.normacontrol.application.dto.request.LoginRequest;
import ru.normacontrol.application.dto.request.RefreshTokenRequest;
import ru.normacontrol.application.dto.request.RegisterRequest;
import ru.normacontrol.application.dto.response.AuthResponse;
import ru.normacontrol.application.usecase.AuthUseCase;

/**
 * REST-контроллер аутентификации и регистрации.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Аутентификация", description = "Регистрация, вход и обновление токенов")
public class AuthController {

    private final AuthUseCase authUseCase;

    @PostMapping("/register")
    @Operation(summary = "Регистрация нового пользователя")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authUseCase.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Вход по логину и паролю")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authUseCase.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Обновление access-токена по refresh-токену")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authUseCase.refresh(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/oauth2/success")
    @Operation(summary = "Callback после успешной OAuth2-аутентификации")
    public ResponseEntity<AuthResponse> oauth2Success(
            @RequestParam("access_token") String accessToken,
            @RequestParam("refresh_token") String refreshToken) {
        AuthResponse response = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .build();
        return ResponseEntity.ok(response);
    }
}
