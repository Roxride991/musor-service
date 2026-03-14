package com.example.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "telegram_login_sessions")
public class TelegramLoginSession {

    @Id
    @Column(name = "session_id", nullable = false, length = 96, updatable = false)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TelegramLoginSessionStatus status;

    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(name = "phone", length = 12)
    private String phone;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "bot_timestamp")
    private Long botTimestamp;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
