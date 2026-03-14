package com.example.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type; // ONE_TIME, SUBSCRIPTION

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status; // PENDING, SUCCEEDED, CANCELED, FAILED

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "external_id", length = 100, unique = true)
    private String externalId; // ID из ЮKassa/CloudPayments

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "confirmation_url", length = 500)
    private String confirmationUrl;

    @Builder.Default
    @Column(name = "currency", length = 3, nullable = false)
    private String currency = "RUB";

    @Column(name = "idempotence_key", length = 64)
    private String idempotenceKey;

    @Column(name = "provider_payload", length = 4000)
    private String providerPayload;

    // Связь с заказом ИЛИ подпиской
    @OneToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @OneToOne
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
