package com.example.core.dto;

import com.example.core.model.PaymentProviderKind;
import com.example.core.model.PaymentStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class PaymentCheckoutResponse {
    Long paymentId;
    PaymentStatus status;
    PaymentProviderKind provider;
    String externalId;
    String confirmationUrl;
    BigDecimal amount;
    String currency;
    String message;
}
