package com.example.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса на отправку OTP кода.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendCodeRequest {

    @NotBlank(message = "Телефон обязателен")
    @Pattern(regexp = "^\\+7\\d{10}$", message = "Неверный формат телефона")
    private String phone;
}

