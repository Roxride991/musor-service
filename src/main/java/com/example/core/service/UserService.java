package com.example.core.service;

import com.example.core.exception.BadRequestException;
import com.example.core.exception.ForbiddenOperationException;
import com.example.core.exception.ResourceNotFoundException;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // ======================
    // Получение профиля
    // ======================
    public User getProfile(User currentUser) {
        // Защита: пользователь может видеть только свой профиль
        return userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
    }

    // ======================
    // Обновление имени
    // ======================
    @Transactional
    public User updateName(User currentUser, String newName) {
        if (newName == null || newName.trim().isEmpty() || newName.length() > 50) {
            throw new BadRequestException("Имя должно быть от 1 до 50 символов");
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        user.setName(newName.trim());
        return userRepository.save(user);
    }

    // ======================
    // Удаление аккаунта
    // ======================
    @Transactional
    public void deleteAccount(User currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        // Здесь можно добавить:
        // - отмену всех активных заказов
        // - логирование удаления

        userRepository.delete(user);
    }

    // ======================
    // ADMIN: Получение всех пользователей
    // ======================
    public List<User> getAllUsers(User admin) {
        if (admin.getUserRole() != UserRole.ADMIN) {
            throw new ForbiddenOperationException("Только администраторы могут просматривать всех пользователей");
        }
        return userRepository.findAll();
    }
}
