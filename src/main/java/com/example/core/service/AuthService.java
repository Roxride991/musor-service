package com.example.core.service;

import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис аутентификации: регистрация и вход по телефону/паролю.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Регистрирует нового пользователя (CLIENT/COURIER). */
    @Transactional
    public User register(String phone, String name, String password, UserRole userRole) {
        if (userRole == UserRole.ADMIN) {
            throw new IllegalArgumentException("Нельзя зарегистрировать ADMIN через API");
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