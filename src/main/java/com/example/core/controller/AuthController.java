package com.example.core.controller;

import com.example.core.dto.*;
import com.example.core.model.User;
import com.example.core.monitoring.FlowMetricsService;
import com.example.core.security.JwtService;
import com.example.core.service.AuditService;
import com.example.core.service.AuthService;
import com.example.core.service.OtpService;
import com.example.core.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final AuditService auditService;
    private final ClientIpResolver clientIpResolver;
    private final FlowMetricsService flowMetricsService;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            OtpService otpService,
            AuditService auditService,
            ClientIpResolver clientIpResolver,
            FlowMetricsService flowMetricsService
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.otpService = otpService;
        this.auditService = auditService;
        this.clientIpResolver = clientIpResolver;
        this.flowMetricsService = flowMetricsService;
    }


    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        try {
            // 🔒 Проверяем OTP
            if (!otpService.verifyOtp(request.getPhone(), request.getCode(), clientIp)) {
                flowMetricsService.recordAuthFailure();
                auditService.log(
                        "AUTH_REGISTER",
                        "FAILED",
                        null,
                        request.getRole() == null ? null : request.getRole().name(),
                        "USER_PHONE",
                        maskPhone(request.getPhone()),
                        "OTP verification failed",
                        clientIp
                );
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
            auditService.log(
                    "AUTH_REGISTER",
                    "SUCCESS",
                    user,
                    "USER_ID",
                    String.valueOf(user.getId()),
                    "Registration successful",
                    clientIp
            );
            flowMetricsService.recordAuthSuccess();

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    AuthResponse.builder()
                            .id(user.getId())
                            .phone(user.getPhone())
                            .name(user.getName())
                            .role(user.getUserRole())
                            .build()
            );

        } catch (IllegalArgumentException e) {
            flowMetricsService.recordAuthFailure();
            auditService.log(
                    "AUTH_REGISTER",
                    "FAILED",
                    null,
                    request.getRole() == null ? null : request.getRole().name(),
                    "USER_PHONE",
                    maskPhone(request.getPhone()),
                    e.getMessage(),
                    clientIp
            );
            return ResponseEntity.badRequest().body(
                    AuthResponse.builder().message(e.getMessage()).build()
            );
        } catch (IllegalStateException e) {
            flowMetricsService.recordAuthFailure();
            auditService.log(
                    "AUTH_REGISTER",
                    "FAILED",
                    null,
                    request.getRole() == null ? null : request.getRole().name(),
                    "USER_PHONE",
                    maskPhone(request.getPhone()),
                    e.getMessage(),
                    clientIp
            );
            HttpStatus status = e.getMessage() != null && e.getMessage().contains("временно недоступен")
                    ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.TOO_MANY_REQUESTS;
            return ResponseEntity.status(status).body(
                    AuthResponse.builder().message(e.getMessage()).build()
            );
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        try {
            User user = authService.login(request.getPhone(), request.getPassword());
            String token = jwtService.generateToken(user);
            flowMetricsService.recordAuthSuccess();
            auditService.log(
                    "AUTH_LOGIN",
                    "SUCCESS",
                    user,
                    "USER_PHONE",
                    maskPhone(user.getPhone()),
                    "Password login successful",
                    clientIp
            );
            AuthResponse response = AuthResponse.builder()
                    .id(user.getId())
                    .phone(user.getPhone())
                    .name(user.getName())
                    .role(user.getUserRole())
                    .token(token)
                    .build();
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            flowMetricsService.recordAuthFailure();
            auditService.log(
                    "AUTH_LOGIN",
                    "FAILED",
                    null,
                    null,
                    "USER_PHONE",
                    maskPhone(request.getPhone()),
                    e.getMessage(),
                    clientIp
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    AuthResponse.builder()
                            .message(e.getMessage())
                            .build()
            );
        }
    }

    @PostMapping("/send-code")
    public ResponseEntity<SendCodeResponse> sendCode(@Valid @RequestBody SendCodeRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        try {
            otpService.sendSmsWithCode(request.getPhone(), clientIp);
            flowMetricsService.recordAuthSuccess();
            auditService.log(
                    "AUTH_SEND_OTP",
                    "SUCCESS",
                    null,
                    null,
                    "USER_PHONE",
                    maskPhone(request.getPhone()),
                    "OTP sent",
                    clientIp
            );
            return ResponseEntity.ok(new SendCodeResponse("Код отправлен"));
        } catch (IllegalStateException e) {
            flowMetricsService.recordAuthFailure();
            HttpStatus status = e.getMessage() != null && e.getMessage().contains("временно недоступен")
                    ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.TOO_MANY_REQUESTS;
            auditService.log(
                    "AUTH_SEND_OTP",
                    "FAILED",
                    null,
                    null,
                    "USER_PHONE",
                    maskPhone(request.getPhone()),
                    e.getMessage(),
                    clientIp
            );
            return ResponseEntity.status(status)
                    .body(new SendCodeResponse(e.getMessage()));
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || !phone.matches("^\\+7\\d{10}$")) {
            return "+7*** ***-**-**";
        }
        return "+7*** ***-**-" + phone.substring(phone.length() - 2);
    }
}
