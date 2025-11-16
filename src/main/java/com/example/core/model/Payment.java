package com.example.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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
}