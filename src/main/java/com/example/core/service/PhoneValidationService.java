// src/main/java/com/example/core/service/PhoneValidationService.java
package com.example.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PhoneValidationService {

    // Паттерн для российских номеров: +7 затем 10 цифр
    private static final Pattern RUSSIAN_PHONE_PATTERN =
            Pattern.compile("^\\+7\\d{10}$");

    /**
     * Проверяет, является ли номер российским и валидным
     */
    public ValidationResult validateRussianPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return ValidationResult.invalid("Номер телефона не может быть пустым");
        }

        // Нормализуем номер
        String normalized = normalizePhone(phoneNumber);

        // Проверяем общий формат
        if (!RUSSIAN_PHONE_PATTERN.matcher(normalized).matches()) {
            return ValidationResult.invalid(
                    "Неверный формат. Требуется российский номер в формате +7XXXXXXXXXX"
            );
        }

        // Проверяем, что номер начинается с +79 (мобильный)
        if (!normalized.startsWith("+79")) {
            return ValidationResult.invalid(
                    "Требуется мобильный номер, начинающийся с +79"
            );
        }

        return ValidationResult.valid(normalized, PhoneType.MOBILE);
    }

    /**
     * Нормализует номер к формату +7XXXXXXXXXX
     */
    public String normalizePhone(String phoneNumber) {
        if (phoneNumber == null) return null;

        // Убираем все нецифровые символы, кроме +
        String digits = phoneNumber.replaceAll("[^+\\d]", "");

        // Убираем пробелы
        digits = digits.replaceAll("\\s+", "");

        // Если уже в формате +7XXXXXXXXXX - возвращаем как есть
        if (digits.matches("^\\+7\\d{10}$")) {
            return digits;
        }

        // Если начинается с 8 и 11 цифр - заменяем 8 на +7
        if (digits.matches("^8\\d{10}$")) {
            return "+7" + digits.substring(1);
        }

        // Если 10 цифр без кода (например, 9001234567)
        if (digits.matches("^9\\d{9}$")) {
            return "+7" + digits;
        }

        // Если 11 цифр без + (например, 79001234567)
        if (digits.matches("^7\\d{10}$")) {
            return "+" + digits;
        }

        // Не смогли нормализовать
        return phoneNumber;
    }

    // Результат валидации
    public static class ValidationResult {
        private final boolean valid;
        private final String normalizedPhone;
        private final PhoneType phoneType;
        private final String errorMessage;

        private ValidationResult(boolean valid, String normalizedPhone,
                                 PhoneType phoneType, String errorMessage) {
            this.valid = valid;
            this.normalizedPhone = normalizedPhone;
            this.phoneType = phoneType;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid(String phone, PhoneType type) {
            return new ValidationResult(true, phone, type, null);
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, null, PhoneType.INVALID, error);
        }

        // Геттеры
        public boolean isValid() { return valid; }
        public String getNormalizedPhone() { return normalizedPhone; }
        public PhoneType getPhoneType() { return phoneType; }
        public String getErrorMessage() { return errorMessage; }
    }

    // Типы телефонов
    public enum PhoneType {
        MOBILE,     // Мобильный
        LANDLINE,   // Стационарный
        INVALID     // Невалидный
    }
}