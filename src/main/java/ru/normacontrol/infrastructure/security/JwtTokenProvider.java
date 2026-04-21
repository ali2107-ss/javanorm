package ru.normacontrol.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.User;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Провайдер JWT-токенов: генерация и валидация access-токенов.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpiration = accessTokenExpiration;
    }

    /**
     * Генерация access-токена.
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        String roles = user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("username", user.getUsername())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Извлечь user ID из токена.
     */
    public String getUserIdFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Извлечь роли из токена.
     */
    public String getRolesFromToken(String token) {
        return getClaims(token).get("roles", String.class);
    }

    /**
     * Валидация токена.
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT токен истёк: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Неподдерживаемый JWT: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Некорректный JWT: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("Невалидная подпись JWT: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims пуст: {}", e.getMessage());
        }
        return false;
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
