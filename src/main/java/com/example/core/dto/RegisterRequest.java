package com.example.core.dto;

import com.example.core.model.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса на регистрацию пользователя.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Телефон обязателен")
    @Pattern(regexp = "^\\+7\\d{10}$", message = "Неверный формат телефона. Пример: +79051234567")
    private String phone;

    @NotBlank(message = "Имя обязательно")
    @Pattern(regexp = "^[а-яА-ЯёЁa-zA-Z\\s\\-]{2,50}$", message = "Имя должно быть от 2 до 50 символов, только буквы, пробелы и дефис")
    private String name;

    @NotBlank(message = "Пароль обязателен")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,}$", 
            message = "Пароль должен быть не менее 8 символов, содержать буквы и цифры")
    private String password;

    @NotNull(message = "Роль обязательна")
    private UserRole role;

    @NotBlank private String code;
}