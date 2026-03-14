package com.example.core.service.payment;

import com.example.core.model.PaymentStatus;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaymentGatewayResult {
    String externalId;
    PaymentStatus status;
    String confirmationUrl;
    String rawPayload;
}
