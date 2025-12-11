package com.example.core.controller;

import com.example.core.dto.mapper.TelegramAuthRequest;
import com.example.core.service.TelegramAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth/telegram")
@RequiredArgsConstructor
public class TelegramAuthController {

    private final TelegramAuthService telegramAuthService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody TelegramAuthRequest request) {
        try {
            log.info("Telegram login attempt for user ID: {}", request.getTelegramId());

            Map<String, Object> authResult = telegramAuthService.authenticateTelegram(request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", authResult,
                    "message", "Авторизация успешна"
            ));

        } catch (TelegramAuthService.PhoneRequiredException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "error", "PHONE_REQUIRED",
                    "message", e.getMessage(),
                    "instructions", "Предоставьте доступ к номеру телефона в Telegram"
            ));

        } catch (TelegramAuthService.InvalidPhoneException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "error", "INVALID_PHONE",
                    "message", e.getMessage(),
                    "requirements", "Требуется российский номер в формате +7XXXXXXXXXX"
            ));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "error", "SECURITY_ERROR",
                    "message", e.getMessage()
            ));

        } catch (Exception e) {
            log.error("Telegram auth error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "SERVER_ERROR",
                    "message", "Ошибка сервера"
            ));
        }
    }

    /**
     * Проверяет доступность Telegram авторизации
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
                "available", true,
                "message", "Telegram авторизация доступна"
        ));
    }
}