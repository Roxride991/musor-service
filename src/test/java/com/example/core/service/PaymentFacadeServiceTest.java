package com.example.core.service;

import com.example.core.dto.PaymentInitRequest;
import com.example.core.exception.ForbiddenOperationException;
import com.example.core.mapper.EntityDtoMapper;
import com.example.core.model.Order;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentFacadeServiceTest {

    @Test
    void initOrderPaymentShouldRejectForeignOrderForClient() {
        PaymentService paymentService = mock(PaymentService.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        EntityDtoMapper entityDtoMapper = mock(EntityDtoMapper.class);

        PaymentFacadeService facadeService = new PaymentFacadeService(
                paymentService,
                orderRepository,
                subscriptionRepository,
                entityDtoMapper
        );

        User orderOwner = User.builder()
                .id(1L)
                .userRole(UserRole.CLIENT)
                .phone("+79990000001")
                .password("x")
                .name("Owner")
                .build();

        User currentUser = User.builder()
                .id(2L)
                .userRole(UserRole.CLIENT)
                .phone("+79990000002")
                .password("x")
                .name("Other")
                .build();

        Order order = Order.builder()
                .id(99L)
                .client(orderOwner)
                .build();

        when(orderRepository.findById(99L)).thenReturn(Optional.of(order));

        assertThrows(
                ForbiddenOperationException.class,
                () -> facadeService.initOrderPayment(currentUser, 99L, new PaymentInitRequest())
        );
    }
}
