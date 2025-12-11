package com.example.core.controller;

import com.example.core.service.TelegramBotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramBotService telegramBotService;
    private final ObjectMapper objectMapper;

    @Value("${telegram.bot.username:musoren_service_bot}")
    private String botUsername;

    /**
     * Endpoint для получения вебхуков от Telegram
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody String payload) {
        try {
            log.debug("Telegram webhook received: {}", payload);

            JsonNode root = objectMapper.readTree(payload);

            // Проверяем, что это сообщение
            if (root.has("message")) {
                JsonNode message = root.get("message");

                if (message.has("text") && message.has("chat")) {
                    String text = message.get("text").asText();
                    Long chatId = message.get("chat").get("id").asLong();
                    String firstName = message.get("chat").get("first_name").asText();

                    // Обрабатываем команды
                    handleCommand(chatId, text, firstName);
                }
            }

            // Всегда возвращаем OK для Telegram
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error handling Telegram webhook", e);
            return ResponseEntity.ok().build(); // Всегда OK для Telegram
        }
    }

    /**
     * Обработка команд от пользователя
     */
    private void handleCommand(Long chatId, String text, String firstName) {
        switch (text) {
            case "/start":
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
            
            1. <b>С авторизацией:</b>
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
            String url = "https://api.telegram.org/bot" +
                    telegramBotService.getBotToken() +
                    "/setWebhook?url=" + webhookUrl;

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
                    "message", "Webhook deleted"
            ));

        } catch (Exception e) {
            log.error("Error deleting webhook", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}