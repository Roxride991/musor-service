package com.example.core.service.payment;

import com.example.core.model.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPaymentGatewayClient implements PaymentGatewayClient {

    @Override
    public PaymentGatewayResult createPayment(PaymentCreateCommand command) {
        String externalId = "mock_" + UUID.randomUUID();
        String confirmationUrl = command.getReturnUrl() == null || command.getReturnUrl().isBlank()
                ? null
                : command.getReturnUrl();

        return PaymentGatewayResult.builder()
                .externalId(externalId)
                .status(PaymentStatus.SUCCEEDED)
                .confirmationUrl(confirmationUrl)
                .rawPayload("{\"provider\":\"mock\",\"id\":\"" + externalId + "\"}")
                .build();
    }

    @Override
    public PaymentGatewayResult fetchPayment(String externalId) {
        return PaymentGatewayResult.builder()
                .externalId(externalId)
                .status(PaymentStatus.SUCCEEDED)
                .confirmationUrl(null)
                .rawPayload("{\"provider\":\"mock\",\"id\":\"" + externalId + "\"}")
                .build();
    }
}
