package com.example.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int CODE_LENGTH = 6;
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration SEND_RATE_LIMIT_WINDOW = Duration.ofMinutes(10);
    private static final Duration VERIFY_RATE_LIMIT_WINDOW = Duration.ofMinutes(10);
    private static final int MAX_SENDS_PER_IP_WINDOW = 30;
    private static final int MAX_VERIFICATIONS_PER_IP_WINDOW = 60;
    private static final String SMS_URL = "https://sms.ru/sms/send";
    private static final String CLIENT_KEY_UNKNOWN = "unknown";

    @Value("${sms.ru.api-id}")
    private String smsApiId;

    @Value("${integration.sms.max-retries:2}")
    private int smsMaxRetries;

    @Value("${integration.sms.retry-backoff-ms:300}")
    private long smsRetryBackoffMs;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public String generateAndStoreOtp(String phone) {
        int bound = (int) Math.pow(10, CODE_LENGTH);
        String code = String.format("%0" + CODE_LENGTH + "d", random.nextInt(bound));
        setWithTtl(codeKey(phone), code, CODE_TTL);
        redisTemplate.delete(failAttemptsKey(phone));
        return code;
    }

    /**
     * Отправляет SMS с кодом на указанный номер.
     * @throws IllegalStateException если отправка не удалась
     */
    public void sendSmsWithCode(String phone, String clientIp) {
        String safeClientKey = normalizeClientKey(clientIp);
        String code;
        try {
            enforcePerIpRateLimit(sendIpRateKey(safeClientKey), SEND_RATE_LIMIT_WINDOW, MAX_SENDS_PER_IP_WINDOW,
                    "Слишком много запросов кода. Попробуйте позже");
            enforcePhoneCooldown(phone);
            code = generateAndStoreOtp(phone);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Сервис подтверждения временно недоступен", e);
        }

        // Формируем тело запроса (POST)
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("api_id", smsApiId);
        body.add("to", phone);
        body.add("msg", "Ваш код подтверждения: " + code);
        body.add("json", "1"); // Обязательно — для удобства парсинга

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body);

        try {
            ResponseEntity<String> response = executeSmsRequestWithRetry(request, phone);

            // Парсим JSON-ответ
            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.path("status").asText();
            int statusCode = root.path("status_code").asInt();

            if (!"OK".equals(status) || statusCode != 100) {
                String error = root.path("status_text").asText("Неизвестная ошибка");
                throw new IllegalStateException("Ошибка SMS.ru: " + error + " (код " + statusCode + ")");
            }

            log.info(
                    "OTP SMS отправлено на {}, clientIp={}, smsId={}",
                    maskPhone(phone),
                    safeClientKey,
                    root.path("sms").path(phone).path("sms_id").asText()
            );

        } catch (RestClientException e) {
            rollbackGeneratedOtp(phone);
            log.error("Сетевая ошибка при отправке SMS на {}: {}", maskPhone(phone), e.getMessage(), e);
            throw new IllegalStateException("Не удалось подключиться к SMS-сервису", e);
        } catch (Exception e) {
            rollbackGeneratedOtp(phone);
            log.error("Ошибка при отправке SMS на {}: {}", maskPhone(phone), e.getMessage(), e);
            throw new IllegalStateException("Не удалось отправить SMS", e);
        }
    }

    public boolean verifyOtp(String phone, String code, String clientIp) {
        String safeClientKey = normalizeClientKey(clientIp);
        try {
            enforcePerIpRateLimit(
                    verifyIpRateKey(safeClientKey),
                    VERIFY_RATE_LIMIT_WINDOW,
                    MAX_VERIFICATIONS_PER_IP_WINDOW,
                    "Слишком много попыток подтверждения. Попробуйте позже"
            );

            String storedCode = redisTemplate.opsForValue().get(codeKey(phone));
            if (storedCode == null || storedCode.isBlank()) {
                return false;
            }

            if (storedCode.equals(code)) {
                redisTemplate.delete(codeKey(phone));
                redisTemplate.delete(failAttemptsKey(phone));
                log.info("OTP подтвержден для {}, clientIp={}", maskPhone(phone), safeClientKey);
                return true;
            }

            long failures = incrementWithTtl(failAttemptsKey(phone), CODE_TTL);
            if (failures >= MAX_VERIFY_ATTEMPTS) {
                redisTemplate.delete(codeKey(phone));
                redisTemplate.delete(failAttemptsKey(phone));
                log.warn("OTP лимит попыток исчерпан для {}, clientIp={}", maskPhone(phone), safeClientKey);
            }
            return false;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Сервис подтверждения временно недоступен", e);
        }
    }

    public void sendSmsWithCode(String phone) {
        sendSmsWithCode(phone, CLIENT_KEY_UNKNOWN);
    }

    public boolean verifyOtp(String phone, String code) {
        return verifyOtp(phone, code, CLIENT_KEY_UNKNOWN);
    }

    private void enforcePhoneCooldown(String phone) {
        try {
            String cooldownKey = cooldownKey(phone);
            Boolean set = redisTemplate.opsForValue().setIfAbsent(
                    cooldownKey,
                    "1",
                    REQUEST_COOLDOWN.toSeconds(),
                    TimeUnit.SECONDS
            );
            if (!Boolean.TRUE.equals(set)) {
                throw new IllegalStateException("Повторный запрос можно отправить через 60 секунд");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Сервис подтверждения временно недоступен", e);
        }
    }

    private void enforcePerIpRateLimit(String key, Duration ttl, int maxAllowed, String message) {
        long count = incrementWithTtl(key, ttl);
        if (count > maxAllowed) {
            throw new IllegalStateException(message);
        }
    }

    private long incrementWithTtl(String key, Duration ttl) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                throw new IllegalStateException("Сервис подтверждения временно недоступен");
            }
            if (count == 1L) {
                redisTemplate.expire(key, ttl);
            }
            return count;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Сервис подтверждения временно недоступен", e);
        }
    }

    private void setWithTtl(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl.toSeconds(), TimeUnit.SECONDS);
    }

    private void rollbackGeneratedOtp(String phone) {
        redisTemplate.delete(codeKey(phone));
        redisTemplate.delete(failAttemptsKey(phone));
    }

    private ResponseEntity<String> executeSmsRequestWithRetry(
            HttpEntity<MultiValueMap<String, String>> request,
            String phone
    ) {
        int attempts = Math.max(1, smsMaxRetries + 1);
        RestClientException lastClientException = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(SMS_URL, request, String.class);
                if (response.getStatusCode() == HttpStatus.OK) {
                    return response;
                }

                if (!isRetriableStatus(response.getStatusCode()) || attempt == attempts) {
                    throw new IllegalStateException("SMS.ru вернул статус: " + response.getStatusCode());
                }

                log.warn(
                        "SMS.ru временно недоступен для {} (status={}, attempt {}/{})",
                        maskPhone(phone),
                        response.getStatusCode(),
                        attempt,
                        attempts
                );
                sleepBeforeRetry(attempt);
            } catch (RestClientException e) {
                lastClientException = e;
                if (attempt == attempts) {
                    break;
                }
                log.warn(
                        "Сетевая ошибка SMS.ru для {} (attempt {}/{}): {}",
                        maskPhone(phone),
                        attempt,
                        attempts,
                        e.getMessage()
                );
                sleepBeforeRetry(attempt);
            }
        }

        if (lastClientException != null) {
            throw new IllegalStateException("Не удалось подключиться к SMS-сервису", lastClientException);
        }
        throw new IllegalStateException("Не удалось отправить SMS");
    }

    private boolean isRetriableStatus(HttpStatusCode status) {
        return status.value() == HttpStatus.TOO_MANY_REQUESTS.value() || status.is5xxServerError();
    }

    private void sleepBeforeRetry(int attempt) {
        long base = Math.max(50L, smsRetryBackoffMs);
        long delay = Math.min(2500L, base * attempt);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private String codeKey(String phone) {
        return "otp:code:" + phone;
    }

    private String cooldownKey(String phone) {
        return "otp:cooldown:" + phone;
    }

    private String failAttemptsKey(String phone) {
        return "otp:verify:fail:" + phone;
    }

    private String sendIpRateKey(String clientKey) {
        return "otp:send:ip:" + clientKey;
    }

    private String verifyIpRateKey(String clientKey) {
        return "otp:verify:ip:" + clientKey;
    }

    private String normalizeClientKey(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return CLIENT_KEY_UNKNOWN;
        }
        return clientIp.trim();
    }

    private String maskPhone(String phone) {
        if (phone == null || !phone.matches("^\\+7\\d{10}$")) {
            return "+7*** ***-**-**";
        }
        return "+7*** ***-**-" + phone.substring(phone.length() - 2);
    }
}
