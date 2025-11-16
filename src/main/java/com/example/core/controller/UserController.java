package com.example.core.controller;

import com.example.core.dto.UpdateNameRequest;
import com.example.core.dto.UserResponse;
import com.example.core.dto.mapper.DtoMapper;
import com.example.core.model.User;
import com.example.core.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер управления профилем пользователя.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final DtoMapper dtoMapper;

    public UserController(UserService userService, DtoMapper dtoMapper) {
        this.userService = userService;
        this.dtoMapper = dtoMapper;
    }

    /**
     * Получение профиля текущего пользователя.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal User currentUser) {
        try {
            User user = userService.getProfile(currentUser);
            UserResponse response = dtoMapper.toUserResponse(user);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Обновление имени текущего пользователя.
     */
    @PatchMapping("/me/name")
    public ResponseEntity<UserResponse> updateName(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateNameRequest request) {
        try {
            User user = userService.updateName(currentUser, request.getName());
            UserResponse response = dtoMapper.toUserResponse(user);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}

