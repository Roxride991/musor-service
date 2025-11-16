package com.example.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса на обновление имени пользователя.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNameRequest {
    @NotBlank(message = "Имя обязательно")
    @Size(min = 2, max = 50, message = "Имя должно быть от 2 до 50 символов")
    @Pattern(regexp = "^[а-яА-ЯёЁa-zA-Z\\s\\-]{2,50}$", message = "Имя должно содержать только буквы, пробелы и дефис")
    private String name;
}

