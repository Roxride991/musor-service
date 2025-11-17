package com.example.core.controller;

import com.example.core.dto.AuthResponse;
import com.example.core.dto.LoginRequest;
import com.example.core.dto.RegisterRequest;
import com.example.core.model.User;
import com.example.core.security.JwtService;
import com.example.core.service.AuthService;
import com.example.core.service.OtpService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final OtpService otpService;

    public AuthController(AuthService authService, JwtService jwtService, OtpService otpService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.otpService = otpService;
    }


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
            return ResponseEntity.badRequest().body(
                    AuthResponse.builder()
                            .message(e.getMessage())
                            .build()
            );
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            User user = authService.login(request.getPhone(), request.getPassword());
            String token = jwtService.generateToken(user);
            AuthResponse response = AuthResponse.builder()
                    .id(user.getId())
                    .phone(user.getPhone())
                    .name(user.getName())
                    .role(user.getUserRole())
                    .token(token)
                    .build();
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    AuthResponse.builder()
                            .message(e.getMessage())
                            .build()
            );
        }
    }

    @PostMapping("/send-code")
    public ResponseEntity<Map<String, String>> sendCode(@RequestBody Map<String, String> payload) {
        String phone = payload.get("phone");

        if (phone == null || phone.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Генерируем код (в продакшене — отправляем SMS)
        String code = otpService.generateAndStoreOtp(phone);

        // Возвращаем для тестирования (в продакшене — просто OK)
        return ResponseEntity.ok(Map.of("code", code, "message", "Код отправлен"));
    }
}