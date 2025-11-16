package com.example.core.controller;

import com.example.core.dto.AuthResponse;
import com.example.core.dto.LoginRequest;
import com.example.core.dto.RegisterRequest;
import com.example.core.service.AuthService;
import com.example.core.model.User;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер аутентификации: регистрация и вход пользователя по паролю.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /** Внедрение сервиса аутентификации. */
    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Регистрация нового пользователя (CLIENT или COURIER). */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = authService.register(
                    request.getPhone(), 
                    request.getName(), 
                    request.getPassword(), 
                    request.getRole()
            );
            AuthResponse response = AuthResponse.builder()
                    .id(user.getId())
                    .phone(user.getPhone())
                    .name(user.getName())
                    .role(user.getUserRole())
                    .build();
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            // Ошибки валидации: дубль телефона, недопустимая роль и т.д.
            return ResponseEntity.badRequest().body(
                    AuthResponse.builder()
                            .message(e.getMessage())
                            .build()
            );
        }
    }

    /** Вход по телефону и паролю. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            User user = authService.login(request.getPhone(), request.getPassword());
            AuthResponse response = AuthResponse.builder()
                    .id(user.getId())
                    .phone(user.getPhone())
                    .name(user.getName())
                    .role(user.getUserRole())
                    .build();
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            // Ошибки: пользователь не найден, неверный пароль
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    AuthResponse.builder()
                            .message(e.getMessage())
                            .build()
            );
        }
    }
}