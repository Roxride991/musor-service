package com.example.core.service;

import com.example.core.dto.mapper.TelegramAuthRequest;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.UserRepository;
import com.example.core.security.JwtService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PhoneValidationService phoneValidationService;
    private final PasswordEncoder passwordEncoder;
    private final TelegramBotService telegramBotService;

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
     * Telegram отправляет данные в формате initData
     */
    public boolean verifyTelegramData(TelegramAuthRequest request) {
        try {
            // 1. Проверка времени (данные действительны 24 часа)
            long currentTime = Instant.now().getEpochSecond();
            long dataAge = currentTime - request.getAuthDate();

            if (dataAge > 86400) { // 24 часа
                log.warn("Expired Telegram data: {} seconds old", dataAge);
                return false;
            }

            // 2. Подготавливаем данные для проверки
            Map<String, String> dataCheck = new TreeMap<>();

            // Добавляем все поля, которые были в initData
            dataCheck.put("auth_date", request.getAuthDate().toString());
            dataCheck.put("first_name", request.getFirstName());
            dataCheck.put("id", request.getTelegramId().toString());

            if (request.getLastName() != null && !request.getLastName().isEmpty()) {
                dataCheck.put("last_name", request.getLastName());
            }

            if (request.getUsername() != null && !request.getUsername().isEmpty()) {
                dataCheck.put("username", request.getUsername());
            }

            if (request.getPhotoUrl() != null && !request.getPhotoUrl().isEmpty()) {
                dataCheck.put("photo_url", request.getPhotoUrl());
            }

            // Важно: phone_number добавляем только если он есть
            if (request.getPhoneNumber() != null && !request.getPhoneNumber().isEmpty()) {
                dataCheck.put("phone_number", request.getPhoneNumber());
            }

            // 3. Создаем строку для проверки в формате "key=value\n"
            StringBuilder dataCheckString = new StringBuilder();
            for (Map.Entry<String, String> entry : dataCheck.entrySet()) {
                dataCheckString.append(entry.getKey())
                        .append("=")
                        .append(entry.getValue())
                        .append("\n");
            }

            // Убираем последний \n
            if (dataCheckString.length() > 0) {
                dataCheckString.setLength(dataCheckString.length() - 1);
            }

            log.debug("Data check string: {}", dataCheckString);

            // 4. Вычисляем секретный ключ из токена бота
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] secretKey = digest.digest(botToken.getBytes(StandardCharsets.UTF_8));

            // 5. Вычисляем HMAC-SHA256 подпись
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, "HmacSHA256");
            sha256HMAC.init(secretKeySpec);

            byte[] hashBytes = sha256HMAC.doFinal(
                    dataCheckString.toString().getBytes(StandardCharsets.UTF_8)
            );

            // 6. Конвертируем в hex (нижний регистр)
            StringBuilder hexHash = new StringBuilder();
            for (byte b : hashBytes) {
                hexHash.append(String.format("%02x", b));
            }

            String calculatedHash = hexHash.toString();
            String receivedHash = request.getHash();

            // 7. Сравниваем хэши (регистр важен!)
            boolean isValid = calculatedHash.equals(receivedHash);

            if (!isValid) {
                log.error("Telegram hash mismatch!");
                log.error("Calculated: {}", calculatedHash);
                log.error("Received: {}", receivedHash);
                log.error("Data string: {}", dataCheckString);
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error verifying Telegram data", e);
            return false;
        }
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