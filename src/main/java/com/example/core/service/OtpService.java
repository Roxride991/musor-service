package com.example.core.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для генерации и проверки OTP кодов.
 * В MVP: коды хранятся в памяти с TTL 10 минут.
 * В продакшене рекомендуется использовать Redis.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int CODE_LENGTH = 4;
    private static final int CODE_TTL_MINUTES = 10;
    private final Random random = new Random();

    // Хранилище кодов: phone -> OtpData
    private final Map<String, OtpData> otpStorage = new ConcurrentHashMap<>();

    /**
     * Генерирует и сохраняет OTP код для телефона.
     * @param phone номер телефона
     * @return сгенерированный код
     */
    public String generateAndStoreOtp(String phone) {
        // Очищаем старые коды для этого телефона
        otpStorage.remove(phone);

        // Генерируем новый код
        String code = generateCode();

        // Сохраняем с временем истечения
        OtpData otpData = new OtpData(code, LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES));
        otpStorage.put(phone, otpData);

        // В MVP просто возвращаем код (в продакшене отправляем через SMS)
        // TODO: Интеграция с SMS-сервисом (SMS.ru, Twilio и т.д.)
        return code;
    }

    /**
     * Проверяет OTP код для телефона.
     * @param phone номер телефона
     * @param code код для проверки
     * @return true если код валиден, false иначе
     */
    public boolean verifyOtp(String phone, String code) {
        OtpData otpData = otpStorage.get(phone);
        
        if (otpData == null) {
            return false;
        }

        // Проверяем срок действия
        if (otpData.expiresAt.isBefore(LocalDateTime.now())) {
            otpStorage.remove(phone);
            return false;
        }

        // Проверяем код
        boolean isValid = otpData.code.equals(code);
        
        // Удаляем код после использования (одноразовый)
        if (isValid) {
            otpStorage.remove(phone);
        }

        return isValid;
    }

    /**
     * Генерирует случайный 4-значный код.
     */
    private String generateCode() {
        // В MVP генерируем 4-значный код
        // Для тестирования можно использовать "0000" если нужно
        int code = 1000 + random.nextInt(9000);
        return String.valueOf(code);
    }

    /**
     * Внутренний класс для хранения данных OTP.
     */
    private static class OtpData {
        final String code;
        final LocalDateTime expiresAt;

        OtpData(String code, LocalDateTime expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }
}

