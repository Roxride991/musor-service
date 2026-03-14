package com.example.core.controller;

import com.example.core.dto.telegram.TelegramInitResponse;
import com.example.core.dto.telegram.TelegramStatusResponse;
import com.example.core.dto.telegram.TelegramVerifyRequest;
import com.example.core.monitoring.FlowMetricsService;
import com.example.core.service.TelegramLoginService;
import com.example.core.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
@RestController
@RequestMapping("/api/auth/telegram")
@RequiredArgsConstructor
public class TelegramAuthController {

    private final TelegramLoginService telegramLoginService;
    private final ClientIpResolver clientIpResolver;
    private final FlowMetricsService flowMetricsService;

    @PostMapping("/init")
    public ResponseEntity<?> init() {
        try {
            TelegramInitResponse response = telegramLoginService.initSession();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to initialize telegram login session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "INIT_FAILED",
                    "message", "Не удалось создать сессию Telegram-входа"
            ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(
            @RequestParam("session_id") String sessionId,
            @RequestParam(name = "consume", defaultValue = "false") boolean consume,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolve(request);
        try {
            TelegramStatusResponse response = consume
                    ? telegramLoginService.consumeVerifiedSession(sessionId, clientIp)
                    : telegramLoginService.getStatus(sessionId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "INVALID_SESSION",
                    "message", e.getMessage()
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "error", "SESSION_NOT_FOUND",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to get telegram login status for session {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "STATUS_FAILED",
                    "message", "Не удалось получить статус сессии"
            ));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(
            @Valid @RequestBody TelegramVerifyRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String clientIp = clientIpResolver.resolve(httpServletRequest);
        try {
            var response = telegramLoginService.verifyByBot(request, clientIp);
            flowMetricsService.recordTelegramVerifySuccess();
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            flowMetricsService.recordTelegramVerifyFailure();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "INVALID_REQUEST",
                    "message", e.getMessage()
            ));
        } catch (SecurityException e) {
            flowMetricsService.recordTelegramVerifyFailure();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "INVALID_SIGNATURE",
                    "message", e.getMessage()
            ));
        } catch (NoSuchElementException e) {
            flowMetricsService.recordTelegramVerifyFailure();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "error", "SESSION_NOT_FOUND",
                    "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            flowMetricsService.recordTelegramVerifyFailure();
            String lowerMessage = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            HttpStatus status = (lowerMessage.contains("недоступ") || lowerMessage.contains("счетчик"))
                    ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.CONFLICT;
            return ResponseEntity.status(status).body(Map.of(
                    "success", false,
                    "error", "SESSION_STATE_ERROR",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            flowMetricsService.recordTelegramVerifyFailure();
            log.error("Failed to verify telegram contact for session {}", request.getSessionId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "VERIFY_FAILED",
                    "message", "Ошибка подтверждения Telegram-сессии"
            ));
        }
    }
}
