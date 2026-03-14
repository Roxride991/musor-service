package com.example.core.service;

import com.example.core.dto.PaymentCheckoutResponse;
import com.example.core.model.Order;
import com.example.core.model.Subscription;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.PaymentRepository;
import com.example.core.service.payment.MockPaymentGatewayClient;
import com.example.core.service.payment.YooKassaPaymentGatewayClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    @Test
    void initOrderPaymentWithMockProviderShouldSucceed() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        YooKassaPaymentGatewayClient yooKassaClient = mock(YooKassaPaymentGatewayClient.class);
        AuditService auditService = mock(AuditService.class);
        NotificationService notificationService = mock(NotificationService.class);

        PaymentService service = new PaymentService(
                paymentRepository,
                new MockPaymentGatewayClient(),
                yooKassaClient,
                auditService,
                notificationService
        );

        ReflectionTestUtils.setField(service, "providerRaw", "MOCK");
        ReflectionTestUtils.setField(service, "defaultReturnUrl", "http://localhost:5173/profile");

        User client = User.builder()
                .id(1L)
                .name("Client")
                .phone("+79990000001")
                .password("x")
                .userRole(UserRole.CLIENT)
                .build();

        Order order = Order.builder()
                .id(11L)
                .client(client)
                .build();

        when(paymentRepository.findFirstByOrderId(11L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            com.example.core.model.Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(101L);
            }
            return payment;
        });

        PaymentCheckoutResponse response = service.initOrderPayment(
                client,
                order,
                BigDecimal.valueOf(350),
                null,
                "Test payment"
        );

        assertNotNull(response);
        assertEquals(101L, response.getPaymentId());
        assertEquals("RUB", response.getCurrency());
        verify(notificationService, times(1)).enqueueInApp(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void resolveAmountsShouldUseServerValues() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        YooKassaPaymentGatewayClient yooKassaClient = mock(YooKassaPaymentGatewayClient.class);
        AuditService auditService = mock(AuditService.class);
        NotificationService notificationService = mock(NotificationService.class);

        PaymentService service = new PaymentService(
                paymentRepository,
                new MockPaymentGatewayClient(),
                yooKassaClient,
                auditService,
                notificationService
        );
        ReflectionTestUtils.setField(service, "oneTimeOrderAmountRub", BigDecimal.valueOf(420));

        Order order = Order.builder().id(7L).build();
        Subscription subscription = Subscription.builder()
                .id(9L)
                .price(BigDecimal.valueOf(1999))
                .build();

        assertEquals(new BigDecimal("420.00"), service.resolveOrderAmount(order));
        assertEquals(new BigDecimal("1999.00"), service.resolveSubscriptionAmount(subscription));
    }
}
