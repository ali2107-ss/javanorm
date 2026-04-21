package ru.normacontrol.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.infrastructure.persistence.entity.RefreshTokenJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.RefreshTokenJpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Сервис управления refresh-токенами.
 * Хранит refresh-токены в БД с TTL 30 дней.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final long REFRESH_TOKEN_DAYS = 30;
    private final RefreshTokenJpaRepository refreshTokenRepository;

    /**
     * Создать новый refresh-токен для пользователя.
     */
    @Transactional
    public String createRefreshToken(UUID userId) {
        String token = UUID.randomUUID().toString();

        RefreshTokenJpaEntity entity = RefreshTokenJpaEntity.builder()
                .token(token)
                .userId(userId)
                .expiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_DAYS))
                .build();

        refreshTokenRepository.save(entity);
        return token;
    }

    /**
     * Валидация refresh-токена. Возвращает userId при успешной валидации.
     */
    @Transactional(readOnly = true)
    public UUID validateRefreshToken(String token) {
        RefreshTokenJpaEntity entity = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadCredentialsException("Невалидный refresh token"));

        if (entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Refresh token истёк");
        }

        return entity.getUserId();
    }

    /**
     * Отозвать refresh-токен.
     */
    @Transactional
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token)
                .ifPresent(refreshTokenRepository::delete);
    }

    /**
     * Отозвать все refresh-токены пользователя.
     */
    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
