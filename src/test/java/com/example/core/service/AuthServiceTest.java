package com.example.core.service;

import com.example.core.model.UserRole;
import com.example.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void registerCourierShouldBeBlockedWhenSelfRegistrationDisabled() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthService authService = new AuthService(userRepository, passwordEncoder);

        ReflectionTestUtils.setField(authService, "allowCourierSelfRegistration", false);
        when(userRepository.existsByPhone("+79990000001")).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(
                        "+79990000001",
                        "Courier",
                        "password1",
                        UserRole.COURIER
                )
        );
    }
}
