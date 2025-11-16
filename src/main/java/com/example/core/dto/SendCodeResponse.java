package com.example.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ответа на запрос отправки OTP кода.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendCodeResponse {
    private String message;
    // В MVP возвращаем код для тестирования
    // В продакшене код не должен возвращаться в ответе
    private String code; // TODO: убрать в продакшене
}

