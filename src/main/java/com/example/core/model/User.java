package com.example.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Пользователь доменной модели.
 * Содержит основную информацию о пользователе и его роли в системе.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_phone", columnNames = {"phone"})
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false)
    private Long id;

    @Column(name = "phone", nullable = false, length = 12)
    @Pattern(regexp = "^\\+7\\d{10}$", message = "Неверный формат телефона")
    private String phone;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Convert(converter = RoleConverter.class)
    @Column(name = "role", nullable = false, length = 32)
    private UserRole userRole;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}


