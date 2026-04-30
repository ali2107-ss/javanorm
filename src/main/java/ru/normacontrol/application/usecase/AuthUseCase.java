package ru.normacontrol.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.application.dto.request.LoginRequest;
import ru.normacontrol.application.dto.request.RefreshTokenRequest;
import ru.normacontrol.application.dto.response.AuthResponse;
import ru.normacontrol.domain.entity.Role;
import ru.normacontrol.domain.entity.User;
import ru.normacontrol.domain.enums.RoleName;
import ru.normacontrol.domain.repository.UserRepository;
import ru.normacontrol.infrastructure.audit.AuditLogged;
import ru.normacontrol.infrastructure.exception.RateLimitExceededException;
import ru.normacontrol.infrastructure.security.BruteForceService;
import ru.normacontrol.infrastructure.security.JwtTokenProvider;
import ru.normacontrol.infrastructure.security.RefreshTokenService;
import ru.normacontrol.application.dto.request.RegisterRequest;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Use case for user registration and authentication.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final BruteForceService bruteForceService;

    /**
     * Register a new local user account.
     *
     * @param request registration payload
     * @return issued JWT tokens
     */
    @Transactional
    @AuditLogged(action = "REGISTER", resourceType = "USER")
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Пользователь с таким именем уже существует");
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
        log.info("Зарегистрирован новый пользователь: {}", user.getUsername());
        return generateTokens(user);
    }

    /**
     * Authenticate a user by login and password.
     *
     * @param request login request
     * @return issued JWT tokens
     */
    @Transactional
    @AuditLogged(action = "LOGIN", resourceType = "USER")
    public AuthResponse login(LoginRequest request) {
        if (bruteForceService.isBlocked(request.getLogin())) {
            throw new RateLimitExceededException("Аккаунт временно заблокирован из-за превышения попыток входа");
        }

        User user = userRepository.findByEmail(request.getLogin())
                .or(() -> userRepository.findByUsername(request.getLogin()))
                .orElseThrow(() -> new BadCredentialsException("Неверный логин или пароль"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            bruteForceService.recordFailedAttempt(request.getLogin());
            throw new BadCredentialsException("Неверный логин или пароль");
        }

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Аккаунт отключён");
        }

        bruteForceService.resetAttempts(request.getLogin());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Пользователь авторизован: {}", user.getUsername());
        return generateTokens(user);
    }

    /**
     * Refresh JWT tokens by refresh token.
     *
     * @param request refresh request
     * @return newly issued tokens
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        UUID userId = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Пользователь не найден"));

        refreshTokenService.revokeRefreshToken(request.getRefreshToken());
        log.info("Токен обновлён для пользователя: {}", user.getUsername());
        return generateTokens(user);
    }

    /**
     * Logout user by revoking their refresh token.
     *
     * @param accessToken optional access token (can be blacklisted in Redis)
     * @param refreshToken refresh token to revoke
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (refreshToken != null) {
            refreshTokenService.revokeRefreshToken(refreshToken);
        }
        // Access token blacklisting could be added here using Redis if required.
        log.info("Пользователь вышел из системы");
    }

    private AuthResponse generateTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .build();
    }
}
