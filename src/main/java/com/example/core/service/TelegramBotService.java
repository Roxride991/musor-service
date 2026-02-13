package com.example.core.service;

import com.example.core.model.User;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class TelegramBotService {

    private final RestTemplate restTemplate;

    @Getter
    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.webapp-url:}")
    private String webAppUrl;

    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot";

    public TelegramBotService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Проверяет, что токен бота валиден
     */
    private void validateBotToken() {
        if (botToken == null || botToken.trim().isEmpty()) {
            throw new IllegalStateException("Telegram bot token is not configured");
        }
    }

    /**
     * Отправляет сообщение пользователю
     */
    public void sendMessage(Long chatId, String text) {
        try {
            validateBotToken();
            String url = TELEGRAM_API_URL + botToken + "/sendMessage";
            log.debug("Sending telegram message to chatId={}", chatId);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put("text", text);
            requestBody.put("parse_mode", "HTML");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Message sent successfully to chat {}: {}", chatId, text);
            } else {
                log.error("Failed to send message to chat {}: Status={}, Body={}",
                        chatId, response.getStatusCode(), response.getBody());
            }

        } catch (Exception e) {
            log.error("Error sending Telegram message to chat {}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Отправляет сообщение с кнопкой для авторизации через Web App
     */
    public void sendLoginButton(Long chatId, String username) {
        try {
            validateBotToken();
            String url = TELEGRAM_API_URL + botToken + "/sendMessage";

            // Если webAppUrl не указан, используем относительный путь
            String webAppUrlToUse = (webAppUrl != null && !webAppUrl.trim().isEmpty())
                    ? webAppUrl
                    : "https://t.me/" + username + "?start=webapp";

            // Создаем inline-клавиатуру с кнопкой Web App
            Map<String, Object> webApp = new HashMap<>();
            webApp.put("url", webAppUrlToUse);

            Map<String, Object> loginButton = new HashMap<>();
            loginButton.put("text", "🔑 Войти на сайт");
            loginButton.put("web_app", webApp);

            Map<String, Object> keyboard = new HashMap<>();
            keyboard.put("inline_keyboard", new Object[][]{{loginButton}});

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put("text", "Для входа на сайт Musoren нажмите кнопку ниже:");
            requestBody.put("reply_markup", keyboard);
            requestBody.put("parse_mode", "HTML");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Login button sent successfully to chat {}", chatId);
            } else {
                log.error("Failed to send login button to chat {}: Status={}, Body={}",
                        chatId, response.getStatusCode(), response.getBody());
            }

        } catch (Exception e) {
            log.error("Error sending login button to chat {}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Отправляет приветственное сообщение при команде /start
     */
    public void sendWelcomeMessage(Long chatId, String firstName) {
        String message = String.format("""
            👋 Привет, %s!
            
            Добро пожаловать в <b>Musoren</b> — сервис вывоза мусора!
            
            🔹 Для использования сервиса вам необходимо:
            1. Нажать кнопку "Войти на сайт"
            2. Предоставить доступ к номеру телефона
            3. Создавать заказы на вывоз мусора
            
            📱 <b>Ваш номер телефона нужен для:</b>
            • Связи с курьером
            • Подтверждения заказов
            • Уведомлений о статусе
            
            Для начала работы нажмите кнопку ниже 👇
            """, firstName).trim();

        sendMessage(chatId, message);
    }

    /**
     * Отправляет инструкцию по авторизации
     */
    public void sendAuthInstructions(Long chatId) {
        String message = """
            🔐 <b>Инструкция по авторизации:</b>
            
            1. Нажмите кнопку "Войти на сайт"
            2. В открывшемся окне разрешите доступ к номеру телефона
            3. Если номер российский (+7XXXXXXXXXX) — авторизация пройдет успешно
            4. Если номер не российский — вы получите сообщение об ошибке
            
            ❗ <b>Требования:</b>
            • Российский номер телефона
            • Подтвержденный аккаунт Telegram
            • Доступ к номеру в настройках контактов
            
            Проблемы с авторизацией? Напишите /help
            """;

        sendMessage(chatId, message);
    }

    /**
     * Отправляет уведомление о успешной авторизации
     */
    public void sendAuthSuccessMessage(Long chatId, User user) {
        String message = String.format("""
            ✅ <b>Авторизация успешна!</b>
            
            Добро пожаловать, %s!
            
            📱 Ваш номер: %s
            👤 Ваше имя: %s
            🎫 Роль: %s
            
            Теперь вы можете:
            • Создавать заказы на вывоз мусора
            • Отслеживать статус заказов
            • Использовать подписки
            
            Для создания заказа нажмите кнопку "Создать заказ" на сайте.
            """,
                user.getName(),
                user.getPhone(),
                user.getName(),
                user.getUserRole()
        ).trim();

        sendMessage(chatId, message);
    }

    /**
     * Отправляет сообщение об ошибке авторизации
     */
    public void sendAuthErrorMessage(Long chatId, String error) {
        String message = String.format("""
            ❌ <b>Ошибка авторизации</b>
            
            Причина: %s
            
            🔧 <b>Что делать:</b>
            1. Проверьте, что номер российский (+7XXXXXXXXXX)
            2. Убедитесь, что разрешили доступ к номеру
            3. Попробуйте еще раз
            
            Если проблема persists, напишите /support
            """, error).trim();

        sendMessage(chatId, message);
    }

    /**
     * Отправляет уведомление о новом заказе
     */
    public void sendOrderNotification(Long chatId, String orderDetails) {
        String message = String.format("""
            🎉 <b>Новый заказ создан!</b>
            
            %s
            
            📊 Вы можете отслеживать статус заказа на сайте.
            """, orderDetails).trim();

        sendMessage(chatId, message);
    }

    /**
     * Устанавливает меню-кнопку для бота
     */
    public void setBotMenu(String botUsername) {
        try {
            validateBotToken();
            String url = TELEGRAM_API_URL + botToken + "/setChatMenuButton";

            // Если webAppUrl не указан, используем относительный путь
            String webAppUrlToUse = (webAppUrl != null && !webAppUrl.trim().isEmpty())
                    ? webAppUrl
                    : "https://t.me/" + botUsername + "?start=webapp";

            // Создаем меню-кнопку
            Map<String, Object> menuButton = new HashMap<>();
            menuButton.put("type", "web_app");
            menuButton.put("text", "Войти на сайт");

            Map<String, Object> webApp = new HashMap<>();
            webApp.put("url", webAppUrlToUse);
            menuButton.put("web_app", webApp);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("menu_button", menuButton);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Bot menu button set successfully");
            } else {
                log.error("Failed to set bot menu: {}",response.getBody());
            }

        } catch (Exception e) {
            log.error("Error setting bot menu", e);
        }
    }
}
