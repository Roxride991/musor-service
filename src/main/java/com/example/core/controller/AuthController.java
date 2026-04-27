package com.example.core.controller;

import com.example.core.dto.AuthResponse;
import com.example.core.dto.LoginRequest;
import com.example.core.dto.RegisterRequest;
import com.example.core.dto.SendCodeRequest;
import com.example.core.dto.SendCodeResponse;
import com.example.core.service.AuthFacadeService;
import com.example.core.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthFacadeService authFacadeService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(authFacadeService.register(request, clientIp));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        return ResponseEntity.ok(authFacadeService.login(request, clientIp));
    }

    @PostMapping("/send-code")
    public ResponseEntity<SendCodeResponse> sendCode(
            @Valid @RequestBody SendCodeRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        return ResponseEntity.ok(authFacadeService.sendCode(request, clientIp));
    }
}
