package ru.normacontrol.presentation.controller;

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
import ru.normacontrol.infrastructure.audit.AuditLogged;

/**
 * REST controller for authentication operations.
 */
@RestController
@RequestMapping({"/v1/auth", "/api/v1/auth"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authUseCase;

    /**
     * Register a new user.
     *
     * @param request registration request
     * @return issued tokens
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authUseCase.register(request));
    }

    /**
     * Authenticate user credentials.
     *
     * @param request login request
     * @return issued tokens
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authUseCase.login(request));
    }

    /**
     * Refresh JWT token pair.
     *
     * @param request refresh request
     * @return new tokens
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authUseCase.refresh(request));
    }

    /**
     * Log out current session.
     *
     * @param authHeader optional access token header
     * @param request refresh token request
     * @return empty response
     */
    @PostMapping("/logout")
    @AuditLogged(action = "LOGOUT", resourceType = "USER")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody RefreshTokenRequest request) {
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }
        authUseCase.logout(accessToken, request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
