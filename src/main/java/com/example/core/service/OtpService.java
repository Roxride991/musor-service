package com.example.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int CODE_LENGTH = 6;
    private static final int CODE_TTL_MINUTES = 10;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final long REQUEST_COOLDOWN_MILLIS = 60_000L;
    private static final String SMS_URL = "https://sms.ru/sms/send";

    @Value("${sms.ru.api-id}")
    private String smsApiId;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    // Хранилище: phone -> OtpData
    private final Map<String, OtpData> otpStorage = new ConcurrentHashMap<>();

    // ⏱️ Защита от флуда: phone -> lastRequestTime
    private final Map<String, Long> lastRequestTime = new ConcurrentHashMap<>();

    public String generateAndStoreOtp(String phone) {
        cleanupExpiredEntries();
        otpStorage.remove(phone);
        int bound = (int) Math.pow(10, CODE_LENGTH);
        String code = String.format("%0" + CODE_LENGTH + "d", random.nextInt(bound));
        otpStorage.put(phone, new OtpData(
                code,
                LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES),
                MAX_VERIFY_ATTEMPTS
        ));
        return code;
    }

    /**
     * Отправляет SMS с кодом на указанный номер.
     * @throws IllegalStateException если отправка не удалась
     */
    public void sendSmsWithCode(String phone) {
        cleanupExpiredEntries();

        // 🔒 Рейт-лимит: не чаще 1 раза в 60 секунд
        long now = System.currentTimeMillis();
        Long last = lastRequestTime.get(phone);
        if (last != null && (now - last) < REQUEST_COOLDOWN_MILLIS) {
            throw new IllegalStateException("Повторный запрос можно отправить через 60 секунд");
        }

        String code = generateAndStoreOtp(phone);

        // Формируем тело запроса (POST)
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("api_id", smsApiId);
        body.add("to", phone);
        body.add("msg", "Ваш код подтверждения: " + code);
        body.add("json", "1"); // Обязательно — для удобства парсинга

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(SMS_URL, request, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new IllegalStateException("SMS.ru вернул статус: " + response.getStatusCode());
            }

            // Парсим JSON-ответ
            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.path("status").asText();
            int statusCode = root.path("status_code").asInt();

            if (!"OK".equals(status) || statusCode != 100) {
                String error = root.path("status_text").asText("Неизвестная ошибка");
                throw new IllegalStateException("Ошибка SMS.ru: " + error + " (код " + statusCode + ")");
            }

            // Успешно — обновляем время последнего запроса
            lastRequestTime.put(phone, now);
            log.info("SMS отправлено на {}, ID: {}", phone, root.path("sms").path(phone).path("sms_id").asText());

        } catch (RestClientException e) {
            log.error("Сетевая ошибка при отправке SMS на {}: {}", phone, e.getMessage(), e);
            throw new IllegalStateException("Не удалось подключиться к SMS-сервису", e);
        } catch (Exception e) {
            log.error("Ошибка при отправке SMS на {}: {}", phone, e.getMessage(), e);
            throw new IllegalStateException("Не удалось отправить SMS", e);
        }
    }

    public boolean verifyOtp(String phone, String code) {
        cleanupExpiredEntries();
        OtpData data = otpStorage.get(phone);
        if (data == null) {
            return false;
        }

        if (data.expiresAt.isBefore(LocalDateTime.now())) {
            otpStorage.remove(phone);
            return false;
        }

        if (data.code.equals(code)) {
            otpStorage.remove(phone);
            return true;
        }

        data.attemptsRemaining--;
        if (data.attemptsRemaining <= 0) {
            otpStorage.remove(phone);
            log.warn("OTP attempts exhausted for {}", phone);
        }
        return false;
    }

    private void cleanupExpiredEntries() {
        LocalDateTime now = LocalDateTime.now();
        otpStorage.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expiresAt.isBefore(now);
            if (expired) {
                lastRequestTime.remove(entry.getKey());
            }
            return expired;
        });
    }

    private static class OtpData {
        final String code;
        final LocalDateTime expiresAt;
        int attemptsRemaining;

        OtpData(String code, LocalDateTime expiresAt, int attemptsRemaining) {
            this.code = code;
            this.expiresAt = expiresAt;
            this.attemptsRemaining = attemptsRemaining;
        }
    }
}
