package com.example.core.controller;

import com.example.core.dto.mapper.TelegramAuthRequest;
import com.example.core.service.TelegramAuthService;
import com.example.core.service.TelegramBotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramBotService telegramBotService;
    private final ObjectMapper objectMapper;
    private final TelegramAuthService telegramAuthService;

    @Value("${telegram.bot.username:musoren_service_bot}")
    private String botUsername;

    /**
     * Endpoint для получения вебхуков от Telegram
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody String payload) {
        try {
            log.info("Telegram webhook received: {}", payload);

            JsonNode root = objectMapper.readTree(payload);

            // Проверяем, что это сообщение
            JsonNode message = root.has("message") ? root.get("message") :
                    root.has("edited_message") ? root.get("edited_message") : null;

            if (message != null && message.has("text") && message.has("chat")) {
                JsonNode chat = message.get("chat");
                String text = message.get("text").asText();
                Long chatId = chat.get("id").asLong();

                // Безопасное получение имени (может отсутствовать)
                String firstName = chat.has("first_name") ?
                        chat.get("first_name").asText() : "Пользователь";

                log.info("Processing message from chat {}: {}", chatId, text);

                // Обрабатываем команды
                handleCommand(chatId, text, firstName);
            } else {
                log.debug("Received update without text message: {}", payload);
            }

            // Всегда возвращаем OK для Telegram
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error handling Telegram webhook: {}", e.getMessage(), e);
            return ResponseEntity.ok().build(); // Всегда OK для Telegram
        }
    }

    /**
     * Обработка данных авторизации из Telegram Web App
     */
    @PostMapping("/auth/telegram")
    public ResponseEntity<?> handleTelegramAuth(@RequestBody TelegramAuthRequest request) {
        try {
            log.info("Telegram auth request received: {}", request);

            Map<String, Object> authResult = telegramAuthService.authenticateTelegram(request);

            return ResponseEntity.ok(authResult);

        } catch (TelegramAuthService.PhoneRequiredException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "PHONE_REQUIRED",
                    "message", "Для использования сервиса необходим номер телефона"
            ));
        } catch (TelegramAuthService.InvalidPhoneException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_PHONE",
                    "message", e.getMessage()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "INVALID_SIGNATURE",
                    "message", "Неверная подпись Telegram данных"
            ));
        } catch (Exception e) {
            log.error("Error during Telegram auth: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "INTERNAL_ERROR",
                    "message", "Ошибка при авторизации"
            ));
        }
    }

    /**
     * Обработка команд от пользователя
     */
    private void handleCommand(Long chatId, String text, String firstName) {
        switch (text) {
            case "/start":
                log.info("Handling command: '{}' for chatId={}", text, chatId);
                telegramBotService.sendWelcomeMessage(chatId, firstName);
                telegramBotService.sendLoginButton(chatId, botUsername);
                break;

            case "/login":
            case "войти":
                telegramBotService.sendAuthInstructions(chatId);
                telegramBotService.sendLoginButton(chatId, botUsername);
                break;

            case "/help":
            case "помощь":
                sendHelpMessage(chatId);
                break;

            case "/support":
                sendSupportMessage(chatId);
                break;

            default:
                // Если не команда, отправляем инструкцию
                if (text.startsWith("/")) {
                    telegramBotService.sendMessage(chatId,
                            "Неизвестная команда. Используйте /start для начала работы.");
                }
                break;
        }
    }

    private void sendHelpMessage(Long chatId) {
        String message = """
            ℹ️ <b>Помощь по использованию бота</b>
            
            <b>Доступные команды:</b>
            /start - Начать работу с ботом
            /login - Получить инструкцию по авторизации
            /help - Показать это сообщение
            /support - Связаться с поддержкой
            
            <b>Частые вопросы:</b>
            Q: Почему нужен номер телефона?
            A: Для связи с курьером и подтверждения заказов
            
            Q: Какой номер нужен?
            A: Российский номер в формате +7XXXXXXXXXX
            
            Q: Не приходит код подтверждения?
            A: Убедитесь, что номер правильный и есть сигнал
            """;

        telegramBotService.sendMessage(chatId, message);
    }

    private void sendSupportMessage(Long chatId) {
        String message = """
            👨‍💼 <b>Техническая поддержка</b>
            
            Если у вас возникли проблемы:
            
            1. <b>С авторизацией:</b>>
               • Проверьте формат номера (+7XXXXXXXXXX)
               • Убедитесь, что разрешили доступ к номеру
            
            2. <b>С созданием заказа:</b>
               • Проверьте, что адрес в зоне обслуживания
               • Убедитесь, что время вывоза корректное
            
            3. <b>Другие вопросы:</b>
               • Напишите на почту: support@musoren.ru
               • Или позвоните: +7 (XXX) XXX-XX-XX
            
            Время работы поддержки: 9:00 - 21:00 (МСК)
            """;

        telegramBotService.sendMessage(chatId, message);
    }

    /**
     * Устанавливает вебхук для бота
     */
    @PostMapping("/set-webhook")
    public ResponseEntity<?> setWebhook(@RequestParam String webhookUrl) {
        try {
            // Правильно формируем URL с кодированием параметра
            String encodedUrl = java.net.URLEncoder.encode(webhookUrl, java.nio.charset.StandardCharsets.UTF_8);
            String url = "https://api.telegram.org/bot" +
                    telegramBotService.getBotToken() +
                    "/setWebhook?url=" + encodedUrl;

            ResponseEntity<String> response = new RestTemplate()
                    .postForEntity(url, null, String.class);

            Map<String, Object> result = new HashMap<>();
            result.put("success", response.getStatusCode().is2xxSuccessful());
            result.put("message", "Webhook set to: " + webhookUrl);
            result.put("response", response.getBody());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error setting webhook", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Удаляет вебхук
     */
    @PostMapping("/delete-webhook")
    public ResponseEntity<?> deleteWebhook() {
        try {
            String url = "https://api.telegram.org/bot" +
                    telegramBotService.getBotToken() +
                    "/deleteWebhook";

            ResponseEntity<String> response = new RestTemplate()
                    .postForEntity(url, null, String.class);

            return ResponseEntity.ok(Map.of(
                    "success", response.getStatusCode().is2xxSuccessful(),
                    "message", "Webhook deleted",
                    "response", response.getBody()
            ));

        } catch (Exception e) {
            log.error("Error deleting webhook", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Получает информацию о вебхуке
     */
    @GetMapping("/webhook-info")
    public ResponseEntity<?> getWebhookInfo() {
        try {
            String url = "https://api.telegram.org/bot" +
                    telegramBotService.getBotToken() +
                    "/getWebhookInfo";

            ResponseEntity<String> response = new RestTemplate()
                    .getForEntity(url, String.class);

            return ResponseEntity.ok(Map.of(
                    "success", response.getStatusCode().is2xxSuccessful(),
                    "info", response.getBody()
            ));

        } catch (Exception e) {
            log.error("Error getting webhook info", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Проверяет, что бот работает
     */
    @GetMapping("/test")
    public ResponseEntity<?> testBot() {
        try {
            String url = "https://api.telegram.org/bot" +
                    telegramBotService.getBotToken() +
                    "/getMe";

            ResponseEntity<String> response = new RestTemplate()
                    .getForEntity(url, String.class);

            return ResponseEntity.ok(Map.of(
                    "success", response.getStatusCode().is2xxSuccessful(),
                    "botInfo", response.getBody()
            ));

        } catch (Exception e) {log.error("Error testing bot", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}