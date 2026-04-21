package ru.normacontrol.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Сервис защиты от Brute Force атак с использованием Redis.
 * Блокирует пользователя на 15 минут после 5 неудачных попыток входа.
 */
@Service
@RequiredArgsConstructor
public class BruteForceService {

    private final StringRedisTemplate redisTemplate;
    private static final int MAX_ATTEMPTS = 5;
    private static final long BLOCK_DURATION_MINUTES = 15;

    public void recordFailedAttempt(String login) {
        String key = "login_attempts:" + login;
        String attemptsStr = redisTemplate.opsForValue().get(key);
        int attempts = attemptsStr == null ? 0 : Integer.parseInt(attemptsStr);
        
        attempts++;
        redisTemplate.opsForValue().set(key, String.valueOf(attempts), BLOCK_DURATION_MINUTES, TimeUnit.MINUTES);
        
        if (attempts >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set("login_blocked:" + login, "true", BLOCK_DURATION_MINUTES, TimeUnit.MINUTES);
        }
    }

    public boolean isBlocked(String login) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("login_blocked:" + login));
    }

    public void resetAttempts(String login) {
        redisTemplate.delete("login_attempts:" + login);
        redisTemplate.delete("login_blocked:" + login);
    }
}
