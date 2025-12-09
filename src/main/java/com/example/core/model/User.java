package com.example.core.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;

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
public class User implements UserDetails {

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

    // ========== UserDetails методы ==========

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Преобразуем UserRole в GrantedAuthority
        return Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + userRole.name())
        );
    }

    @Override
    public String getUsername() {
        // Spring Security использует телефон как username
        return this.phone;
    }

    // Для JWT нам также нужен email/phone в токене
    public String getEmail() {
        // Если у вас есть email поле, используйте его
        // Или возвращаем phone, если email нет
        return this.phone; // или this.email если есть такое поле
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // Дополнительные геттеры для удобства
    public String getPhoneNumber() {
        return phone;
    }

    public String getFullName() {
        return name;
    }
}