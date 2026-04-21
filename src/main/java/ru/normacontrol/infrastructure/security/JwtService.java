package ru.normacontrol.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final StringRedisTemplate redisTemplate;
    private final long accessTtl = 3600000L; // 1 час
    private final long refreshTtl = 2592000000L; // 30 дней

    public JwtService(@Value("${jwt.secret:c2VjcmV0S2V5Rm9yTm9ybWFDb250cm9sQXBwbGljYXRpb25UaGF0SXNMb25nRW5vdWdo}") String secret,
                      StringRedisTemplate redisTemplate) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.redisTemplate = redisTemplate;
    }

    public String generateAccessToken(UUID userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTtl))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("refresh:" + token, userId.toString(), refreshTtl, TimeUnit.MILLISECONDS);
        return token;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
            // Проверка токена в Blacklist (при logout)
            if (Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token))) {
                return false;
            }
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID extractUserId(String token) {
        String subject = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload().getSubject();
        return UUID.fromString(subject);
    }
    
    public String extractRole(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload().get("role", String.class);
    }

    public void logout(String accessToken, String refreshToken) {
        if (refreshToken != null) {
            redisTemplate.delete("refresh:" + refreshToken);
        }
        if (accessToken != null) {
            try {
                Date expiration = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(accessToken).getPayload().getExpiration();
                long ttl = expiration.getTime() - System.currentTimeMillis();
                if (ttl > 0) {
                    redisTemplate.opsForValue().set("blacklist:" + accessToken, "logout", ttl, TimeUnit.MILLISECONDS);
                }
            } catch (JwtException e) {
                // Токен уже истёк, ничего не делаем
            }
        }
    }
    
    public UUID validateRefreshTokenAndGetUserId(String refreshToken) {
        String userIdStr = redisTemplate.opsForValue().get("refresh:" + refreshToken);
        if (userIdStr == null) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        return UUID.fromString(userIdStr);
    }
}
