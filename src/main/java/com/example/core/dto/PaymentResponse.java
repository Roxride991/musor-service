package com.example.core.dto;

import com.example.core.model.PaymentStatus;
import com.example.core.model.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO для ответа с информацией о платеже.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private PaymentType type;
    private PaymentStatus status;
    private BigDecimal amount;
    private String externalId;
    private OffsetDateTime createdAt;
}

