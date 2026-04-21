package ru.normacontrol.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import ru.normacontrol.application.dto.request.LoginRequest;
import ru.normacontrol.application.dto.request.RefreshTokenRequest;
import ru.normacontrol.application.dto.request.RegisterRequest;
import ru.normacontrol.application.dto.response.AuthResponse;
import ru.normacontrol.domain.entity.Role;
import ru.normacontrol.domain.entity.User;
import ru.normacontrol.domain.enums.RoleName;
import ru.normacontrol.domain.repository.UserRepository;
import ru.normacontrol.infrastructure.security.JwtService;
import ru.normacontrol.infrastructure.security.BruteForceService;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final BruteForceService bruteForceService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail()) || userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(request.getEmail())
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .enabled(true)
                .roles(Set.of(Role.builder().id(1L).name(RoleName.ROLE_USER).build()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
                
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(generateTokens(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        if (bruteForceService.isBlocked(request.getLogin())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Аккаунт заблокирован на 15 минут из-за превышения попыток входа");
        }

        User user = userRepository.findByEmail(request.getLogin())
                .or(() -> userRepository.findByUsername(request.getLogin()))
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            bruteForceService.recordFailedAttempt(request.getLogin());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Неверный логин или пароль");
        }

        if (!user.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Аккаунт отключен");
        }

        bruteForceService.resetAttempts(request.getLogin());
        return ResponseEntity.ok(generateTokens(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            UUID userId = jwtService.validateRefreshTokenAndGetUserId(request.getRefreshToken());
            User user = userRepository.findById(userId).orElseThrow();
            
            // Ротация токена - удаляем старый refresh token
            jwtService.logout(null, request.getRefreshToken()); 
            
            return ResponseEntity.ok(generateTokens(user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader, 
            @Valid @RequestBody RefreshTokenRequest request) {
            
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }
        jwtService.logout(accessToken, request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    private AuthResponse generateTokens(User user) {
        String role = user.getRoles().stream()
                .map(r -> r.getName().name())
                .findFirst()
                .orElse("ROLE_USER");
                
        String accessToken = jwtService.generateAccessToken(user.getId(), role);
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .build();
    }
}
