package com.example.core.service;

import com.example.core.model.Order;
import com.example.core.model.OrderStatus;
import com.example.core.model.Subscription;
import com.example.core.model.SubscriptionStatus;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.ServiceZoneRepository;
import com.example.core.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    @Test
    void cancelByClientShouldRestoreSubscriptionUsage() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        ServiceZoneRepository zoneRepository = mock(ServiceZoneRepository.class);
        GeoUtils geoUtils = mock(GeoUtils.class);
        PaymentService paymentService = mock(PaymentService.class);
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);

        OrderService service = new OrderService(
                orderRepository, zoneRepository, geoUtils, paymentService, subscriptionRepository
        );

        User client = User.builder()
                .id(10L)
                .phone("+79990000000")
                .name("Client")
                .password("x")
                .userRole(UserRole.CLIENT)
                .build();

        Subscription subscription = Subscription.builder()
                .id(55L)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .totalAllowedOrders(10)
                .usedOrders(3)
                .user(client)
                .build();

        Order order = Order.builder()
                .id(100L)
                .client(client)
                .subscription(subscription)
                .status(OrderStatus.PUBLISHED)
                .build();

        when(orderRepository.findByIdAndClient(100L, client)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order canceled = service.cancelByClient(100L, client);

        assertEquals(OrderStatus.CANCELLED_BY_CUSTOMER, canceled.getStatus());
        assertEquals(2, subscription.getUsedOrders());
        verify(subscriptionRepository, times(1)).save(subscription);
    }
}
