package com.example.core.service.payment;

import com.example.core.model.PaymentType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class PaymentCreateCommand {
    PaymentType type;
    BigDecimal amount;
    String currency;
    String description;
    String returnUrl;
    Long orderId;
    Long subscriptionId;
    String idempotenceKey;
}
