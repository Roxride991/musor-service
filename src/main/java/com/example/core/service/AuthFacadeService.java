package com.example.core.service;

import com.example.core.dto.AuthResponse;
import com.example.core.dto.LoginRequest;
import com.example.core.dto.RegisterRequest;
import com.example.core.dto.SendCodeRequest;
import com.example.core.dto.SendCodeResponse;
import com.example.core.exception.BadRequestException;
import com.example.core.exception.ConflictException;
import com.example.core.exception.ServiceUnavailableException;
import com.example.core.exception.UnauthorizedException;
import com.example.core.mapper.EntityDtoMapper;
import com.example.core.model.User;
import com.example.core.monitoring.FlowMetricsService;
import com.example.core.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthFacadeService {

    private final AuthService authService;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final AuditService auditService;
    private final FlowMetricsService flowMetricsService;
    private final EntityDtoMapper entityDtoMapper;

    public AuthResponse register(RegisterRequest request, String clientIp) {
        try {
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
                throw new BadRequestException("Неверный или устаревший код подтверждения");
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
            return entityDtoMapper.toAuthResponse(user);
        } catch (IllegalArgumentException ex) {
            flowMetricsService.recordAuthFailure();
            auditService.log(
                    "AUTH_REGISTER",
                    "FAILED",
                    null,
                    request.getRole() == null ? null : request.getRole().name(),
                    "USER_PHONE",
                    maskPhone(request.getPhone()),
                    ex.getMessage(),
                    clientIp
            );
            throw new BadRequestException(ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            flowMetricsService.recordAuthFailure();
            auditService.log(
                    "AUTH_REGISTER",
                    "FAILED",
                    null,
                    request.getRole() == null ? null : request.getRole().name(),
                    "USER_PHONE",
                    maskPhone(request.getPhone()),
                    ex.getMessage(),
                    clientIp
            );
            if (isTemporaryServiceFailure(ex.getMessage())) {
                throw new ServiceUnavailableException(ex.getMessage(), ex);
            }
            throw new ConflictException(ex.getMessage(), ex);
        }
    }

    public AuthResponse login(LoginRequest request, String clientIp) {
        try {
            User user = authService.login(request.getPhone(), request.getPassword());
            AuthResponse response = entityDtoMapper.toAuthResponse(user);
            response.setToken(jwtService.generateToken(user));
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
            return response;
        } catch (IllegalArgumentException ex) {
            flowMetricsService.recordAuthFailure();
            auditService.log(
                    "AUTH_LOGIN",
                    "FAILED",
                    null,
                    null,
                    "USER_PHONE",
                    maskPhone(request.getPhone()),
                    ex.getMessage(),
                    clientIp
            );
            throw new UnauthorizedException(ex.getMessage());
        }
    }

    public SendCodeResponse sendCode(SendCodeRequest request, String clientIp) {
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
            return new SendCodeResponse("Код отправлен");
        } catch (IllegalStateException ex) {
            flowMetricsService.recordAuthFailure();
            auditService.log(
                    "AUTH_SEND_OTP",
                    "FAILED",
                    null,
                    null,
                    "USER_PHONE",
                    maskPhone(request.getPhone()),
                    ex.getMessage(),
                    clientIp
            );
            if (isTemporaryServiceFailure(ex.getMessage())) {
                throw new ServiceUnavailableException(ex.getMessage(), ex);
            }
            throw new ConflictException(ex.getMessage(), ex);
        }
    }

    private boolean isTemporaryServiceFailure(String message) {
        return message != null && message.contains("временно недоступен");
    }

    private String maskPhone(String phone) {
        if (phone == null || !phone.matches("^\\+7\\d{10}$")) {
            return "+7*** ***-**-**";
        }
        return "+7*** ***-**-" + phone.substring(phone.length() - 2);
    }
}
