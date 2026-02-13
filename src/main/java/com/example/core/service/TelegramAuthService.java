package com.example.core.service;

import com.example.core.dto.mapper.TelegramAuthRequest;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.UserRepository;
import com.example.core.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PhoneValidationService phoneValidationService;
    private final PasswordEncoder passwordEncoder;
    private final TelegramBotService telegramBotService;
    private final ObjectMapper objectMapper;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.skip-validation:false}")
    private boolean skipValidation;

    /**
     * Основной метод авторизации через Telegram Web App
     */
    @Transactional
    public Map<String, Object> authenticateTelegram(TelegramAuthRequest request) {
        log.info("Telegram auth attempt for user: {}", request.getTelegramId());

        // 1. Проверяем подпись Telegram (можно временно отключить для тестирования)
        if (!skipValidation && !verifyTelegramData(request)) {
            log.warn("Invalid Telegram signature for user: {}", request.getTelegramId());
            throw new SecurityException("Неверная подпись Telegram данных");
        }

        // 2. Проверяем наличие номера телефона
        if (request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty()) {
            throw new PhoneRequiredException("Для использования сервиса необходим номер телефона");
        }

        // 3. Валидируем российский номер
        PhoneValidationService.ValidationResult validation =
                phoneValidationService.validateRussianPhone(request.getPhoneNumber());

        if (!validation.isValid()) {
            throw new InvalidPhoneException(validation.getErrorMessage());
        }

        String validatedPhone = validation.getNormalizedPhone();

        // 4. Ищем или создаем пользователя
        User user = userRepository.findByPhone(validatedPhone)
                .orElseGet(() -> createNewUser(request, validatedPhone));

        // 5. Обновляем информацию из Telegram
        updateUserFromTelegram(user, request);

        // 6. Устанавливаем верификацию через Telegram
        user.setPhoneVerified(true);
        user.setPhoneVerificationMethod("TELEGRAM");
        user.setPhoneVerificationDate(new Date());
        user.setLastLogin(new Date());

        userRepository.save(user);

        log.info("Successful Telegram auth for phone: {}", validatedPhone);

        // 7. Отправляем уведомление в Telegram об успешной авторизации
        if (request.getTelegramId() != null) {
            try {
                telegramBotService.sendAuthSuccessMessage(request.getTelegramId(), user);
            } catch (Exception e) {
                log.warn("Failed to send Telegram notification, but auth succeeded", e);
            }
        }

        // 8. Генерируем JWT
        String token = jwtService.generateToken(user);

        // 9. Возвращаем результат
        return buildAuthResponse(user, token);
    }

    /**
     * Проверяет подпись Telegram Web App
     * Telegram отправляет данные в формате initData (raw query string).
     */
    public boolean verifyTelegramData(TelegramAuthRequest request) {
        try {
            String initData = request.getInitData();
            if (initData == null || initData.isBlank()) {
                log.warn("Telegram init_data is missing");
                return false;
            }

            Map<String, String> initDataMap = parseInitData(initData);
            String receivedHash = initDataMap.remove("hash");
            if (receivedHash == null || receivedHash.isBlank()) {
                log.warn("Telegram init_data hash is missing");
                return false;
            }

            String authDateRaw = initDataMap.get("auth_date");
            if (authDateRaw == null || authDateRaw.isBlank()) {
                log.warn("Telegram auth_date is missing in init_data");
                return false;
            }

            long authDate = Long.parseLong(authDateRaw);
            long currentTime = Instant.now().getEpochSecond();
            long dataAge = currentTime - authDate;
            if (dataAge < 0 || dataAge > 86400) {
                log.warn("Expired Telegram data: {} seconds old", dataAge);
                return false;
            }

            String dataCheckString = initDataMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("\n"));

            // Telegram WebApp secret key: HMAC_SHA256("WebAppData", bot_token)
            byte[] secretKey = hmacSha256(
                    "WebAppData".getBytes(StandardCharsets.UTF_8),
                    botToken.getBytes(StandardCharsets.UTF_8)
            );
            byte[] calculatedHashBytes = hmacSha256(
                    secretKey,
                    dataCheckString.getBytes(StandardCharsets.UTF_8)
            );
            String calculatedHash = toHex(calculatedHashBytes);

            boolean isValid = MessageDigest.isEqual(
                    calculatedHash.getBytes(StandardCharsets.UTF_8),
                    receivedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
            );
            if (!isValid) {
                log.warn("Telegram hash mismatch for telegramId={}", request.getTelegramId());
                return false;
            }

            // Берем user-данные только из подписанного init_data.
            String userJson = initDataMap.get("user");
            if (userJson == null || userJson.isBlank()) {
                log.warn("Telegram init_data does not contain user object");
                return false;
            }

            JsonNode userNode = objectMapper.readTree(userJson);
            long telegramId = userNode.path("id").asLong(0L);
            if (telegramId <= 0) {
                log.warn("Telegram user id is invalid in init_data");
                return false;
            }
            String firstName = userNode.path("first_name").asText("");
            if (firstName.isBlank()) {
                log.warn("Telegram first_name is missing in init_data");
                return false;
            }

            request.setTelegramId(telegramId);
            request.setFirstName(firstName);
            request.setLastName(userNode.path("last_name").asText(""));
            request.setUsername(userNode.path("username").asText(""));
            request.setPhotoUrl(userNode.path("photo_url").asText(""));
            request.setAuthDate(authDate);

            return true;

        } catch (Exception e) {
            log.error("Error verifying Telegram data", e);
            return false;
        }
    }

    private Map<String, String> parseInitData(String initDataRaw) {
        Map<String, String> data = new HashMap<>();
        String[] parts = initDataRaw.split("&");
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = java.net.URLDecoder.decode(part.substring(0, idx), StandardCharsets.UTF_8);
            String value = java.net.URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
            data.put(key, value);
        }
        return data;
    }

    private byte[] hmacSha256(byte[] key, byte[] payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(payload);
    }

    private String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * Создает нового пользователя из данных Telegram
     */
    private User createNewUser(TelegramAuthRequest request, String phone) {
        // Формируем имя
        String name = request.getFirstName();
        if (request.getLastName() != null && !request.getLastName().isEmpty()) {
            name += " " + request.getLastName();
        }

        // Генерируем случайный пароль
        String randomPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        User user = User.builder()
                .phone(phone)
                .name(name)
                .password(passwordEncoder.encode(randomPassword))
                .userRole(UserRole.CLIENT) // По умолчанию клиент
                .telegramId(request.getTelegramId())
                .telegramUsername(request.getUsername())
                .avatarUrl(request.getPhotoUrl())
                .phoneVerified(true)
                .phoneVerificationMethod("TELEGRAM")
                .phoneVerificationDate(new Date())
                .build();

        log.info("Created new user via Telegram: {} ({})", name, phone);
        return userRepository.save(user);
    }

    /**
     * Обновляет информацию пользователя из Telegram
     */
    private void updateUserFromTelegram(User user, TelegramAuthRequest request) {
        boolean updated = false;

        // Обновляем Telegram ID если изменился
        if (request.getTelegramId() != null &&
                !request.getTelegramId().equals(user.getTelegramId())) {
            user.setTelegramId(request.getTelegramId());
            updated = true;
        }

        // Обновляем username Telegram
        if (request.getUsername() != null &&
                !request.getUsername().equals(user.getTelegramUsername())) {
            user.setTelegramUsername(request.getUsername());
            updated = true;
        }

        // Обновляем аватар
        if (request.getPhotoUrl() != null &&
                !request.getPhotoUrl().equals(user.getAvatarUrl())) {
            user.setAvatarUrl(request.getPhotoUrl());
            updated = true;
        }

        // Обновляем имя из Telegram если оно полнее
        String telegramName = buildFullName(request);
        if (!telegramName.equals(user.getName())) {
            user.setName(telegramName);
            updated = true;
        }

        if (updated) {
            user.setUpdatedAt(new Date());
        }
    }

    /**
     * Формирует полное имя из данных Telegram
     */
    private String buildFullName(TelegramAuthRequest request) {
        String name = request.getFirstName();
        if (request.getLastName() != null && !request.getLastName().isEmpty()) {
            name += " " + request.getLastName();
        }
        return name;
    }

    /**
     * Строит ответ авторизации
     */
    private Map<String, Object> buildAuthResponse(User user, String token) {
        Map<String, Object> response = new HashMap<>();

        response.put("token", token);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", 3600); // 1 час

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("phone", user.getPhone());
        userData.put("phoneVerified", user.isPhoneVerified());
        userData.put("name", user.getName());
        userData.put("role", user.getUserRole());
        userData.put("telegramId", user.getTelegramId());
        userData.put("telegramUsername", user.getTelegramUsername());
        userData.put("avatarUrl", user.getAvatarUrl());

        response.put("user", userData);

        return response;
    }

    // Исключения
    public static class PhoneRequiredException extends RuntimeException {
        public PhoneRequiredException(String message) {
            super(message);
        }
    }

    public static class InvalidPhoneException extends RuntimeException {
        public InvalidPhoneException(String message) {
            super(message);
        }
    }
}
