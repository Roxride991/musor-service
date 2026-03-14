package com.example.core.service;

import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Сервис аутентификации: регистрация и вход по телефону/паролю.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${auth.allow-courier-self-registration:false}")
    private boolean allowCourierSelfRegistration;

    /** Регистрирует нового пользователя (CLIENT/COURIER). */
    @Transactional
    public User register(String phone, String name, String password, UserRole userRole) {
        if (userRole == UserRole.ADMIN) {
            throw new IllegalArgumentException("Нельзя зарегистрировать ADMIN через API");
        }
        if (userRole == UserRole.COURIER && !allowCourierSelfRegistration) {
            throw new IllegalArgumentException("Самостоятельная регистрация курьеров отключена");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("Пользователь с таким телефоном уже существует");
        }

        // Хешируем пароль с помощью BCrypt
        String encodedPassword = passwordEncoder.encode(password);

        User user = User.builder()
                .phone(phone)
                .name(name)
                .password(encodedPassword)
                .userRole(userRole)
                .phoneVerified(true)
                .phoneVerificationMethod("SMS")
                .phoneVerificationDate(new Date())
                .build();

        return userRepository.save(user);
    }

    /**
     * Логин по телефону и паролю.
     * Проверяет пароль с помощью BCrypt.
     */
    public User login(String phone, String password) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // Проверяем пароль с помощью BCrypt
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Неверный пароль");
        }

        return user;
    }
}
