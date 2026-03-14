package com.example.core.service.payment;

public interface PaymentGatewayClient {

    PaymentGatewayResult createPayment(PaymentCreateCommand command);

    PaymentGatewayResult fetchPayment(String externalId);
}
