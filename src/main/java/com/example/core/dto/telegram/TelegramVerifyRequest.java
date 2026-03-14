package com.example.core.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TelegramVerifyRequest {

    @NotBlank(message = "session_id обязателен")
    @JsonProperty("session_id")
    private String sessionId;

    @NotBlank(message = "phone обязателен")
    private String phone;

    @NotNull(message = "telegram_user_id обязателен")
    @JsonProperty("telegram_user_id")
    private Long telegramUserId;

    @NotNull(message = "timestamp обязателен")
    private Long timestamp;

    @NotBlank(message = "signature обязателен")
    private String signature;
}
