package com.example.core.dto;

import com.example.core.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * DTO для ответа с информацией о пользователе.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String phone;
    private String name;
    private UserRole role;
    private OffsetDateTime createdAt;
}

