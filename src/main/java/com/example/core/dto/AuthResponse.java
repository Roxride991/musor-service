package com.example.core.dto;

import com.example.core.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private Long id;
    private String phone;
    private String name;
    private UserRole role;
    private String message; // Для ошибок
}