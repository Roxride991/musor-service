package com.example.core.service;

import com.example.core.dto.telegram.TelegramInitResponse;
import com.example.core.dto.telegram.TelegramStatusResponse;
import com.example.core.dto.telegram.TelegramVerifyRequest;
import com.example.core.model.TelegramLoginSession;
import com.example.core.model.TelegramLoginSessionStatus;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.TelegramLoginSessionRepository;
import com.example.core.repository.UserRepository;
import com.example.core.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramLoginService {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{24,96}$");
    private static final Pattern RUSSIAN_PHONE_PATTERN = Pattern.compile("^\\+7\\d{10}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_VERIFY_ATTEMPTS_PER_SESSION = 5;
    private static final int MAX_VERIFY_ATTEMPTS_PER_IP_WINDOW = 120;
    private static final Duration VERIFY_ATTEMPTS_WINDOW = Duration.ofMinutes(15);

    private final TelegramLoginSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final AuditService auditService;

    @Value("${telegram.auth.bot-username:musoren_service_bot}")
    private String botUsername;

    @Value("${telegram.auth.session-ttl-seconds:600}")
    private long sessionTtlSeconds;

    @Value("${telegram.auth.max-clock-skew-seconds:120}")
    private long maxClockSkewSeconds;

    @Value("${telegram.auth.hmac-secret:}")
    private String hmacSecret;

    @Transactional
    public TelegramInitResponse initSession() {
        validateConfiguration();
        cleanupExpiredSessions();

        String sessionId = generateSessionId();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(sessionTtlSeconds);

        TelegramLoginSession session = TelegramLoginSession.builder()
                .sessionId(sessionId)
                .status(TelegramLoginSessionStatus.PENDING)
                .expiresAt(expiresAt)
                .build();
        sessionRepository.save(session);

        return TelegramInitResponse.builder()
                .sessionId(sessionId)
                .status(TelegramLoginSessionStatus.PENDING.name())
                .expiresAt(expiresAt.toString())
                .ttlSeconds(sessionTtlSeconds)
                .startUrl(buildStartUrl(sessionId))
                .build();
    }

    @Transactional(readOnly = true)
    public TelegramStatusResponse getStatus(String rawSessionId) {
        String sessionId = sanitizeSessionId(rawSessionId);

        TelegramLoginSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Сессия авторизации не найдена"));

        if (isExpired(session)) {
            return TelegramStatusResponse.builder()
                    .sessionId(session.getSessionId())
                    .status(TelegramLoginSessionStatus.EXPIRED.name())
                    .expiresAt(session.getExpiresAt().toString())
                    .authenticated(false)
                    .consumed(session.getConsumedAt() != null)
                    .message("Сессия истекла, начните авторизацию заново.")
                    .build();
        }

        if (session.getStatus() == TelegramLoginSessionStatus.VERIFIED) {
            boolean alreadyConsumed = session.getConsumedAt() != null;
            if (alreadyConsumed) {
                return TelegramStatusResponse.builder()
                        .sessionId(session.getSessionId())
                        .status(TelegramLoginSessionStatus.VERIFIED.name())
                        .expiresAt(session.getExpiresAt().toString())
                        .verifiedAt(session.getVerifiedAt() != null ? session.getVerifiedAt().toString() : null)
                        .authenticated(false)
                        .consumed(true)
                        .message("Сессия уже использована.")
                        .build();
            }

            return TelegramStatusResponse.builder()
                    .sessionId(session.getSessionId())
                    .status(TelegramLoginSessionStatus.VERIFIED.name())
                    .expiresAt(session.getExpiresAt().toString())
                    .verifiedAt(session.getVerifiedAt() != null ? session.getVerifiedAt().toString() : null)
                    .authenticated(false)
                    .consumed(false)
                    .message("Номер подтвержден. Завершите вход через consume=true.")
                    .build();
        }

        String message = switch (session.getStatus()) {
            case PENDING -> "Ожидаем подтверждение номера в Telegram-боте.";
            case REJECTED -> "Подтверждение отклонено. Запустите вход заново.";
            case EXPIRED -> "Сессия истекла, начните авторизацию заново.";
            default -> "Статус сессии обновлен.";
        };

        return TelegramStatusResponse.builder()
                .sessionId(session.getSessionId())
                .status(session.getStatus().name())
                .expiresAt(session.getExpiresAt().toString())
                .verifiedAt(session.getVerifiedAt() != null ? session.getVerifiedAt().toString() : null)
                .authenticated(false)
                .consumed(session.getConsumedAt() != null)
                .message(message)
                .build();
    }

    @Transactional
    public TelegramStatusResponse consumeVerifiedSession(String rawSessionId, String clientIp) {
        String sessionId = sanitizeSessionId(rawSessionId);
        TelegramLoginSession session = sessionRepository.findBySessionIdForUpdate(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Сессия авторизации не найдена"));

        if (isExpired(session)) {
            session.setStatus(TelegramLoginSessionStatus.EXPIRED);
            session.setLastError("SESSION_EXPIRED");
            sessionRepository.save(session);
            auditService.log(
                    "TELEGRAM_CONSUME_SESSION",
                    "FAILED",
                    null,
                    null,
                    "TELEGRAM_SESSION",
                    abbreviateSessionId(sessionId),
                    "Session expired",
                    clientIp
            );
            return TelegramStatusResponse.builder()
                    .sessionId(session.getSessionId())
                    .status(TelegramLoginSessionStatus.EXPIRED.name())
                    .expiresAt(session.getExpiresAt().toString())
                    .authenticated(false)
                    .consumed(false)
                    .message("Сессия истекла, начните авторизацию заново.")
                    .build();
        }

        if (session.getStatus() != TelegramLoginSessionStatus.VERIFIED) {
            auditService.log(
                    "TELEGRAM_CONSUME_SESSION",
                    "FAILED",
                    null,
                    null,
                    "TELEGRAM_SESSION",
                    abbreviateSessionId(sessionId),
                    "Session is not verified",
                    clientIp
            );
            return TelegramStatusResponse.builder()
                    .sessionId(session.getSessionId())
                    .status(session.getStatus().name())
                    .expiresAt(session.getExpiresAt().toString())
                    .verifiedAt(session.getVerifiedAt() != null ? session.getVerifiedAt().toString() : null)
                    .authenticated(false)
                    .consumed(session.getConsumedAt() != null)
                    .message("Сессия еще не подтверждена.")
                    .build();
        }

        if (session.getConsumedAt() != null) {
            auditService.log(
                    "TELEGRAM_CONSUME_SESSION",
                    "FAILED",
                    null,
                    null,
                    "TELEGRAM_SESSION",
                    abbreviateSessionId(sessionId),
                    "Session already consumed",
                    clientIp
            );
            return TelegramStatusResponse.builder()
                    .sessionId(session.getSessionId())
                    .status(TelegramLoginSessionStatus.VERIFIED.name())
                    .expiresAt(session.getExpiresAt().toString())
                    .verifiedAt(session.getVerifiedAt() != null ? session.getVerifiedAt().toString() : null)
                    .authenticated(false)
                    .consumed(true)
                    .message("Сессия уже использована.")
                    .build();
        }

        User user = resolveUserForLogin(session);
        String token = jwtService.generateToken(user);
        session.setConsumedAt(OffsetDateTime.now());
        sessionRepository.save(session);
        auditService.log(
                "TELEGRAM_CONSUME_SESSION",
                "SUCCESS",
                user,
                "TELEGRAM_SESSION",
                abbreviateSessionId(sessionId),
                "Session consumed successfully",
                clientIp
        );

        return TelegramStatusResponse.builder()
                .sessionId(session.getSessionId())
                .status(TelegramLoginSessionStatus.VERIFIED.name())
                .expiresAt(session.getExpiresAt().toString())
                .verifiedAt(session.getVerifiedAt() != null ? session.getVerifiedAt().toString() : null)
                .authenticated(true)
                .consumed(true)
                .token(token)
                .user(Map.of(
                        "id", user.getId(),
                        "phone", user.getPhone(),
                        "name", user.getName(),
                        "role", user.getUserRole().name(),
                        "phoneVerified", user.isPhoneVerified()
                ))
                .message("Авторизация подтверждена.")
                .build();
    }

    @Transactional
    public Map<String, Object> verifyByBot(TelegramVerifyRequest request, String clientIp) {
        validateConfiguration();

        String sessionId = sanitizeSessionId(request.getSessionId());
        enforceVerifyRateLimit(sessionId, clientIp);
        TelegramLoginSession session = sessionRepository.findBySessionIdForUpdate(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Сессия авторизации не найдена"));

        if (isExpired(session)) {
            session.setStatus(TelegramLoginSessionStatus.EXPIRED);
            session.setLastError("SESSION_EXPIRED");
            sessionRepository.save(session);
            auditService.log(
                    "TELEGRAM_VERIFY",
                    "FAILED",
                    null,
                    null,
                    "TELEGRAM_SESSION",
                    abbreviateSessionId(sessionId),
                    "Session expired",
                    clientIp
            );
            throw new IllegalStateException("Сессия истекла");
        }

        if (session.getStatus() != TelegramLoginSessionStatus.PENDING) {
            auditService.log(
                    "TELEGRAM_VERIFY",
                    "FAILED",
                    null,
                    null,
                    "TELEGRAM_SESSION",
                    abbreviateSessionId(sessionId),
                    "Session already used",
                    clientIp
            );
            throw new IllegalStateException("Сессия уже использована");
        }

        validateTimestamp(request.getTimestamp());

        String normalizedPhone = normalizeRussianPhone(request.getPhone());
        String expectedSignature = calculateHmac(
                sessionId + normalizedPhone + request.getTelegramUserId() + request.getTimestamp()
        );

        if (!secureEquals(expectedSignature, request.getSignature())) {
            session.setStatus(TelegramLoginSessionStatus.REJECTED);
            session.setLastError("SIGNATURE_MISMATCH");
            sessionRepository.save(session);
            auditService.log(
                    "TELEGRAM_VERIFY",
                    "FAILED",
                    null,
                    null,
                    "TELEGRAM_SESSION",
                    abbreviateSessionId(sessionId),
                    "HMAC signature mismatch",
                    clientIp
            );
            throw new SecurityException("Неверная подпись HMAC");
        }

        User user = findOrCreateTelegramUser(normalizedPhone, request.getTelegramUserId());

        session.setStatus(TelegramLoginSessionStatus.VERIFIED);
        session.setTelegramUserId(request.getTelegramUserId());
        session.setPhone(normalizedPhone);
        session.setUserId(user.getId());
        session.setBotTimestamp(request.getTimestamp());
        session.setVerifiedAt(OffsetDateTime.now());
        session.setLastError(null);
        sessionRepository.save(session);

        log.info(
                "Telegram session verified: sessionId={}, telegramUserId={}, phone={}",
                abbreviateSessionId(sessionId),
                request.getTelegramUserId(),
                maskPhone(normalizedPhone)
        );
        auditService.log(
                "TELEGRAM_VERIFY",
                "SUCCESS",
                user,
                "TELEGRAM_SESSION",
                abbreviateSessionId(sessionId),
                "Telegram phone verified",
                clientIp
        );

        return Map.of(
                "success", true,
                "status", TelegramLoginSessionStatus.VERIFIED.name(),
                "session_id", sessionId,
                "verified_at", session.getVerifiedAt().toString()
        );
    }

    @Transactional
    public int cleanupExpiredSessions() {
        return sessionRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
    }

    private User resolveUserForLogin(TelegramLoginSession session) {
        if (session.getUserId() != null) {
            return userRepository.findById(session.getUserId())
                    .orElseThrow(() -> new NoSuchElementException("Пользователь для сессии не найден"));
        }
        if (session.getPhone() != null) {
            return userRepository.findByPhone(session.getPhone())
                    .orElseThrow(() -> new NoSuchElementException("Пользователь по телефону не найден"));
        }
        throw new NoSuchElementException("Сессия не содержит пользователя");
    }

    private User findOrCreateTelegramUser(String phone, Long telegramUserId) {
        User user = userRepository.findByPhone(phone)
                .orElseGet(() -> userRepository.findByTelegramId(telegramUserId).orElse(null));

        if (user == null) {
            String generatedPassword = UUID.randomUUID().toString().replace("-", "");
            user = User.builder()
                    .phone(phone)
                    .name("Пользователь Telegram")
                    .password(passwordEncoder.encode(generatedPassword))
                    .userRole(UserRole.CLIENT)
                    .telegramId(telegramUserId)
                    .phoneVerified(true)
                    .phoneVerificationMethod("TELEGRAM_CONTACT")
                    .phoneVerificationDate(new Date())
                    .lastLogin(new Date())
                    .build();
            return userRepository.save(user);
        }

        user.setTelegramId(telegramUserId);
        user.setPhone(phone);
        if (user.getName() == null || user.getName().isBlank()) {
            user.setName("Пользователь Telegram");
        }
        user.setPhoneVerified(true);
        user.setPhoneVerificationMethod("TELEGRAM_CONTACT");
        user.setPhoneVerificationDate(new Date());
        user.setLastLogin(new Date());
        user.setUpdatedAt(new Date());
        return userRepository.save(user);
    }

    private String sanitizeSessionId(String value) {
        String sessionId = value == null ? "" : value.trim();
        if (!SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw new IllegalArgumentException("Некорректный session_id");
        }
        return sessionId;
    }

    private void validateTimestamp(Long timestamp) {
        if (timestamp == null || timestamp <= 0) {
            throw new IllegalArgumentException("Некорректный timestamp");
        }
        long now = System.currentTimeMillis() / 1000;
        long diff = Math.abs(now - timestamp);
        if (diff > maxClockSkewSeconds) {
            throw new SecurityException("Просроченный timestamp");
        }
    }

    private String normalizeRussianPhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new IllegalArgumentException("Телефон обязателен");
        }

        String digits = rawPhone.replaceAll("[^\\d+]", "");
        if (digits.matches("^8\\d{10}$")) {
            digits = "+7" + digits.substring(1);
        } else if (digits.matches("^7\\d{10}$")) {
            digits = "+" + digits;
        } else if (digits.matches("^\\+7\\d{10}$")) {
            // valid format, do nothing
        } else {
            throw new IllegalArgumentException("Разрешены только российские номера +7XXXXXXXXXX");
        }

        if (!RUSSIAN_PHONE_PATTERN.matcher(digits).matches()) {
            throw new IllegalArgumentException("Разрешены только российские номера +7XXXXXXXXXX");
        }
        if (digits.length() != 12) {
            throw new IllegalArgumentException("Некорректная длина номера телефона");
        }
        return digits;
    }

    private boolean secureEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                right.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }

    private String calculateHmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось вычислить HMAC", e);
        }
    }

    private boolean isExpired(TelegramLoginSession session) {
        return session.getExpiresAt() == null || session.getExpiresAt().isBefore(OffsetDateTime.now());
    }

    private String generateSessionId() {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String buildStartUrl(String sessionId) {
        String normalizedBotUsername = botUsername == null ? "" : botUsername.trim();
        if (normalizedBotUsername.isEmpty()) {
            throw new IllegalStateException("TELEGRAM_BOT_USERNAME не настроен");
        }
        return "https://t.me/" + normalizedBotUsername + "?start=" + sessionId;
    }

    private String maskPhone(String phone) {
        if (phone == null || !phone.matches("^\\+7\\d{10}$")) {
            return "+7*** ***-**-**";
        }
        String suffix = phone.substring(phone.length() - 2);
        return "+7*** ***-**-" + suffix;
    }

    private String abbreviateSessionId(String sessionId) {
        if (sessionId == null || sessionId.length() < 10) {
            return "unknown";
        }
        return sessionId.substring(0, 6) + "..." + sessionId.substring(sessionId.length() - 4);
    }

    private void validateConfiguration() {
        if (sessionTtlSeconds < 60 || sessionTtlSeconds > Duration.ofHours(2).toSeconds()) {
            throw new IllegalStateException("telegram.auth.session-ttl-seconds должен быть от 60 до 7200");
        }
        if (maxClockSkewSeconds < 30 || maxClockSkewSeconds > 600) {
            throw new IllegalStateException("telegram.auth.max-clock-skew-seconds должен быть от 30 до 600");
        }
        if (hmacSecret == null || hmacSecret.isBlank() || hmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("TELEGRAM_AUTH_HMAC_SECRET не настроен или слишком короткий (минимум 32 байта)");
        }
    }

    private void enforceVerifyRateLimit(String sessionId, String clientIp) {
        String safeIp = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        long perSessionAttempts = incrementWithTtl(
                "tg:verify:session:" + sessionId,
                VERIFY_ATTEMPTS_WINDOW
        );
        if (perSessionAttempts > MAX_VERIFY_ATTEMPTS_PER_SESSION) {
            throw new SecurityException("Превышено число попыток подтверждения сессии");
        }

        long perIpAttempts = incrementWithTtl(
                "tg:verify:ip:" + safeIp,
                VERIFY_ATTEMPTS_WINDOW
        );
        if (perIpAttempts > MAX_VERIFY_ATTEMPTS_PER_IP_WINDOW) {
            throw new SecurityException("Превышено число попыток подтверждения с IP");
        }
    }

    private long incrementWithTtl(String key, Duration ttl) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            throw new IllegalStateException("Не удалось сохранить счетчик попыток");
        }
        if (count == 1L) {
            redisTemplate.expire(key, ttl.toSeconds(), TimeUnit.SECONDS);
        }
        return count;
    }
}
