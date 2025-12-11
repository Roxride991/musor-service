package com.example.core.dto.mapper;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TelegramAuthRequest {

    @NotNull(message = "Telegram ID обязателен")
    @JsonProperty("id")
    private Long telegramId;

    @NotNull(message = "Имя обязательно")
    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("username")
    private String username;

    @JsonProperty("photo_url")
    private String photoUrl;

    @NotNull(message = "Дата авторизации обязательна")
    @JsonProperty("auth_date")
    private Long authDate;

    @NotNull(message = "Хэш обязателен")
    @JsonProperty("hash")
    private String hash;

    @JsonProperty("phone_number")
    private String phoneNumber;
}