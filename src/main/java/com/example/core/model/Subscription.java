package com.example.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionPlan plan;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(name = "service_address", length = 255)
    private String serviceAddress;

    @Column(name = "service_lat")
    private Double serviceLat;

    @Column(name = "service_lng")
    private Double serviceLng;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickup_slot", length = 32)
    private PickupSlot pickupSlot;

    @Column(name = "next_pickup_at")
    private OffsetDateTime nextPickupAt;

    @Column(name = "pause_started_at")
    private OffsetDateTime pauseStartedAt;

    @Builder.Default
    @Column(name = "paused_days_used", nullable = false)
    private int pausedDaysUsed = 0;

    @Builder.Default
    @Column(name = "cadence_days", nullable = false)
    private int cadenceDays = 2;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    // 🔹 НОВОЕ: сколько всего вывозов разрешено
    @Column(nullable = false)
    private int totalAllowedOrders;

    // 🔹 НОВОЕ: сколько уже использовано
    @Builder.Default
    @Column(nullable = false)
    private int usedOrders = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;


    public boolean hasAvailableOrders() {
        return usedOrders < totalAllowedOrders && getStatus() == SubscriptionStatus.ACTIVE;
    }

    public int getRemainingOrders() {
        return Math.max(0, totalAllowedOrders - usedOrders);
    }
}
