package com.example.core.controller;

import com.example.core.dto.*;
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
            // 🔒 Проверяем OTP
            if (!otpService.verifyOtp(request.getPhone(), request.getCode())) {
                return ResponseEntity.badRequest().body(
                        AuthResponse.builder()
                                .message("Неверный или устаревший код подтверждения")
                                .build()
                );
            }

            User user = authService.register(
                    request.getPhone(),
                    request.getName(),
                    request.getPassword(),
                    request.getRole()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    AuthResponse.builder()
                            .id(user.getId())
                            .phone(user.getPhone())
                            .name(user.getName())
                            .role(user.getUserRole())
                            .build()
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    AuthResponse.builder().message(e.getMessage()).build()
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
    public ResponseEntity<SendCodeResponse> sendCode(@Valid @RequestBody SendCodeRequest request) {
        try {
            otpService.sendSmsWithCode(request.getPhone());
            return ResponseEntity.ok(new SendCodeResponse("Код отправлен"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new SendCodeResponse(e.getMessage()));
        }
    }
}