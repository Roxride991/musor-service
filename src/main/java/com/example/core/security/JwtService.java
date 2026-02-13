package com.example.core.security;

import com.example.core.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Service
public class JwtService {

    private final SecretKey key;
    private final long validityInMs = 3600_000; // 1 час
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public JwtService(@Value("${jwt.secret:}") String secret, Environment environment) {
        String effectiveSecret = secret;

        if (effectiveSecret == null || effectiveSecret.trim().isEmpty()) {
            boolean localOrTest = Arrays.stream(environment.getActiveProfiles())
                    .anyMatch(profile -> "local".equals(profile) || "test".equals(profile));

            if (!localOrTest) {
                throw new IllegalStateException("JWT secret is not configured");
            }

            byte[] randomBytes = new byte[48];
            SECURE_RANDOM.nextBytes(randomBytes);
            effectiveSecret = Base64.getEncoder().encodeToString(randomBytes);
            log.warn("JWT_SECRET is not configured for local/test profile. Generated ephemeral secret for this process.");
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
