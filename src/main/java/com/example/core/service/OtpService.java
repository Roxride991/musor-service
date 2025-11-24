package com.example.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int CODE_LENGTH = 4;
    private static final int CODE_TTL_MINUTES = 10;
    private static final String SMS_URL = "https://sms.ru/sms/send";

    @Value("${sms.ru.api-id}")
    private String smsApiId;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper; // для парсинга JSON
    private final Random random = new Random();

    // Хранилище: phone -> OtpData
    private final Map<String, OtpData> otpStorage = new ConcurrentHashMap<>();

    // ⏱️ Защита от флуда: phone -> lastRequestTime
    private final Map<String, Long> lastRequestTime = new ConcurrentHashMap<>();

    public String generateAndStoreOtp(String phone) {
        otpStorage.remove(phone);
        String code = String.valueOf(1000 + random.nextInt(9000));
        otpStorage.put(phone, new OtpData(code, LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES)));
        return code;
    }

    /**
     * Отправляет SMS с кодом на указанный номер.
     * @throws IllegalStateException если отправка не удалась
     */
    public void sendSmsWithCode(String phone) {
        // 🔒 Рейт-лимит: не чаще 1 раза в 60 секунд
        long now = System.currentTimeMillis();
        Long last = lastRequestTime.get(phone);
        if (last != null && (now - last) < 60_000) {
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
            System.out.println("✅ SMS отправлено на " + phone + ", ID: " + root.path("sms").path(phone).path("sms_id").asText());

        } catch (RestClientException e) {
            System.err.println("Сетевая ошибка при отправке SMS: " + e.getMessage());
            throw new IllegalStateException("Не удалось подключиться к SMS-сервису", e);
        } catch (Exception e) {
            System.err.println("Ошибка при отправке SMS: " + e.getMessage());
            throw new IllegalStateException("Не удалось отправить SMS", e);
        }
    }

    public boolean verifyOtp(String phone, String code) {
        OtpData data = otpStorage.get(phone);
        if (data == null || data.expiresAt.isBefore(LocalDateTime.now())) {
            otpStorage.remove(phone);
            return false;
        }

        boolean valid = data.code.equals(code);
        if (valid) otpStorage.remove(phone);
        return valid;
    }

    private static class OtpData {
        final String code;
        final LocalDateTime expiresAt;

        OtpData(String code, LocalDateTime expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }
}