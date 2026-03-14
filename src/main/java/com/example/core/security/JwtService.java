package com.example.core.security;

import com.example.core.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Date;

@Slf4j
@Service
public class JwtService {

    private final SecretKey key;
    private final long validityInMs = 3600_000; // 1 час
    private static final String LOCAL_DEV_FALLBACK_SECRET =
            "local-dev-jwt-secret-change-me-please-32-bytes-minimum";

    public JwtService(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.allow-local-fallback:true}") boolean allowLocalFallback,
            Environment environment
    ) {
        String effectiveSecret = secret;

        if (effectiveSecret == null || effectiveSecret.trim().isEmpty()) {
            boolean localOrTest = Arrays.stream(environment.getActiveProfiles())
                    .anyMatch(profile -> "local".equals(profile) || "test".equals(profile));

            if (!localOrTest || !allowLocalFallback) {
                throw new IllegalStateException("JWT secret is not configured");
            }

            effectiveSecret = LOCAL_DEV_FALLBACK_SECRET;
            log.warn("JWT_SECRET is not configured for local/test profile. Using deterministic local fallback secret. Set JWT_ALLOW_LOCAL_FALLBACK=false to disable.");
        }

        byte[] secretBytes = effectiveSecret.getBytes();
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim("role", user.getUserRole().name())
                .claim("phone", user.getPhone())
                .claim("name", user.getName())
                .claim("phoneVerified", user.isPhoneVerified()) // ДОБАВЛЕНО
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + validityInMs))
                .signWith(key)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("role", String.class);
    }

    public String getPhoneFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("phone", String.class);
    }

    public boolean isPhoneVerifiedFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Boolean.TRUE.equals(claims.get("phoneVerified", Boolean.class));
    }

    public boolean validate(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    // ДОБАВЛЕН: Проверяет истек ли токен
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    // ДОБАВЛЕН: Получает время истечения токена
    public Date getExpirationDate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getExpiration();
    }
}
