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
import java.util.Date;

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

    @Column(name = "telegram_id", unique = true)
    private Long telegramId;

    @Column(name = "telegram_username", length = 32)
    private String telegramUsername;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "phone_verified", nullable = true)
    @Builder.Default
    private Boolean phoneVerified = false;

    @Column(name = "phone_verification_method", length = 20)
    private String phoneVerificationMethod;

    @Column(name = "phone_verification_date")
    private Date phoneVerificationDate;

    @Column(name = "banned", nullable = true)
    @Builder.Default
    private Boolean banned = false;

    @Column(name = "ban_reason", length = 500)
    private String banReason;

    @Column(name = "registration_ip", length = 45)
    private String registrationIp;

    @Column(name = "last_login")
    private Date lastLogin;

    @Column(name = "updated_at")
    private Date updatedAt;

    // ========== UserDetails методы ==========

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + userRole.name())
        );
    }

    @Override
    public String getUsername() {
        return this.phone;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !banned; // <-- ВАЖНО: возвращаем противоположное значение banned
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !banned; // <-- ВАЖНО: возвращаем противоположное значение banned
    }

    // Дополнительные методы
    public String getEmail() {
        return this.phone;
    }

    public String getPhoneNumber() {
        return phone;
    }

    public String getFullName() {
        return name;
    }

    // Метод для проверки banned (Lombok сгенерирует isBanned() автоматически)
    // @Getter над классом уже генерирует public boolean isBanned()

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (updatedAt == null) {
            updatedAt = new Date();
        }
        if (lastLogin == null) {
            lastLogin = new Date();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }

    public boolean isBanned() {
        return Boolean.TRUE.equals(this.banned);
    }

    public boolean isPhoneVerified() {
        return Boolean.TRUE.equals(this.phoneVerified);
    }
}