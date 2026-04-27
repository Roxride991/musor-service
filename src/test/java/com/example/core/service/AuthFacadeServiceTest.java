package com.example.core.service;

import com.example.core.dto.LoginRequest;
import com.example.core.exception.UnauthorizedException;
import com.example.core.mapper.EntityDtoMapper;
import com.example.core.monitoring.FlowMetricsService;
import com.example.core.security.JwtService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthFacadeServiceTest {

    @Test
    void loginShouldReturnUnauthorizedWhenCredentialsAreInvalid() {
        AuthService authService = mock(AuthService.class);
        JwtService jwtService = mock(JwtService.class);
        OtpService otpService = mock(OtpService.class);
        AuditService auditService = mock(AuditService.class);
        FlowMetricsService flowMetricsService = mock(FlowMetricsService.class);
        EntityDtoMapper entityDtoMapper = mock(EntityDtoMapper.class);

        AuthFacadeService facadeService = new AuthFacadeService(
                authService,
                jwtService,
                otpService,
                auditService,
                flowMetricsService,
                entityDtoMapper
        );

        LoginRequest request = new LoginRequest();
        request.setPhone("+79990000000");
        request.setPassword("bad-password");

        when(authService.login(request.getPhone(), request.getPassword()))
                .thenThrow(new IllegalArgumentException("Неверный пароль"));

        assertThrows(UnauthorizedException.class, () -> facadeService.login(request, "127.0.0.1"));
    }
}
