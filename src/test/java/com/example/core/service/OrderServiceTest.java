package com.example.core.service;

import com.example.core.dto.OrderAdminFilter;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        SubscriptionSchedulingService subscriptionSchedulingService = mock(SubscriptionSchedulingService.class);
        AuditService auditService = mock(AuditService.class);

        OrderService service = new OrderService(
                orderRepository,
                zoneRepository,
                geoUtils,
                paymentService,
                subscriptionRepository,
                subscriptionSchedulingService,
                auditService
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

    @Test
    void updateStatusByAdminShouldRejectInvalidTransition() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        ServiceZoneRepository zoneRepository = mock(ServiceZoneRepository.class);
        GeoUtils geoUtils = mock(GeoUtils.class);
        PaymentService paymentService = mock(PaymentService.class);
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        SubscriptionSchedulingService subscriptionSchedulingService = mock(SubscriptionSchedulingService.class);
        AuditService auditService = mock(AuditService.class);

        OrderService service = new OrderService(
                orderRepository,
                zoneRepository,
                geoUtils,
                paymentService,
                subscriptionRepository,
                subscriptionSchedulingService,
                auditService
        );

        User admin = User.builder()
                .id(1L)
                .phone("+79990000001")
                .name("Admin")
                .password("x")
                .userRole(UserRole.ADMIN)
                .build();

        Order order = Order.builder()
                .id(77L)
                .status(OrderStatus.PUBLISHED)
                .build();

        when(orderRepository.findById(77L)).thenReturn(Optional.of(order));

        assertThrows(
                IllegalStateException.class,
                () -> service.updateStatusByAdmin(77L, OrderStatus.COMPLETED, admin)
        );
    }

    @Test
    void updateStatusByAdminShouldAllowValidTransition() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        ServiceZoneRepository zoneRepository = mock(ServiceZoneRepository.class);
        GeoUtils geoUtils = mock(GeoUtils.class);
        PaymentService paymentService = mock(PaymentService.class);
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        SubscriptionSchedulingService subscriptionSchedulingService = mock(SubscriptionSchedulingService.class);
        AuditService auditService = mock(AuditService.class);

        OrderService service = new OrderService(
                orderRepository,
                zoneRepository,
                geoUtils,
                paymentService,
                subscriptionRepository,
                subscriptionSchedulingService,
                auditService
        );

        User admin = User.builder()
                .id(1L)
                .phone("+79990000001")
                .name("Admin")
                .password("x")
                .userRole(UserRole.ADMIN)
                .build();

        Order order = Order.builder()
                .id(88L)
                .status(OrderStatus.ACCEPTED)
                .build();

        when(orderRepository.findById(88L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order updated = service.updateStatusByAdmin(88L, OrderStatus.ON_THE_WAY, admin);
        assertEquals(OrderStatus.ON_THE_WAY, updated.getStatus());
    }

    @Test
    void getFilteredOrdersForAdminShouldRejectNonAdmin() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        ServiceZoneRepository zoneRepository = mock(ServiceZoneRepository.class);
        GeoUtils geoUtils = mock(GeoUtils.class);
        PaymentService paymentService = mock(PaymentService.class);
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        SubscriptionSchedulingService subscriptionSchedulingService = mock(SubscriptionSchedulingService.class);
        AuditService auditService = mock(AuditService.class);

        OrderService service = new OrderService(
                orderRepository,
                zoneRepository,
                geoUtils,
                paymentService,
                subscriptionRepository,
                subscriptionSchedulingService,
                auditService
        );

        User client = User.builder()
                .id(1L)
                .phone("+79990000003")
                .name("Client")
                .password("x")
                .userRole(UserRole.CLIENT)
                .build();

        assertThrows(
                IllegalStateException.class,
                () -> service.getFilteredOrdersForAdmin(client, OrderAdminFilter.builder().build(), 100)
        );
    }

    @Test
    void acceptClusterByCourierShouldAcceptOrdersWhenSameDateSlotAndRadius() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        ServiceZoneRepository zoneRepository = mock(ServiceZoneRepository.class);
        GeoUtils geoUtils = mock(GeoUtils.class);
        PaymentService paymentService = mock(PaymentService.class);
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        SubscriptionSchedulingService subscriptionSchedulingService = mock(SubscriptionSchedulingService.class);
        AuditService auditService = mock(AuditService.class);

        OrderService service = new OrderService(
                orderRepository,
                zoneRepository,
                geoUtils,
                paymentService,
                subscriptionRepository,
                subscriptionSchedulingService,
                auditService
        );

        User courier = User.builder()
                .id(500L)
                .phone("+79990000500")
                .name("Courier")
                .password("x")
                .userRole(UserRole.COURIER)
                .build();

        OffsetDateTime pickupTime = OffsetDateTime.now().plusDays(1).withHour(8).withMinute(20).withSecond(0).withNano(0);
        Order first = Order.builder()
                .id(10L)
                .status(OrderStatus.PUBLISHED)
                .pickupTime(pickupTime)
                .lat(51.820000)
                .lng(55.170000)
                .build();
        Order second = Order.builder()
                .id(11L)
                .status(OrderStatus.PUBLISHED)
                .pickupTime(pickupTime.plusMinutes(10))
                .lat(51.820160)
                .lng(55.170020)
                .build();

        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(first));
        when(orderRepository.findByIdWithLock(11L)).thenReturn(Optional.of(second));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Order> accepted = service.acceptClusterByCourier(courier, List.of(10L, 11L), 50.0);

        assertEquals(2, accepted.size());
        assertEquals(OrderStatus.ACCEPTED, first.getStatus());
        assertEquals(OrderStatus.ACCEPTED, second.getStatus());
        assertEquals(courier, first.getCourier());
        assertEquals(courier, second.getCourier());
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    @Test
    void acceptClusterByCourierShouldRejectDifferentSlots() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        ServiceZoneRepository zoneRepository = mock(ServiceZoneRepository.class);
        GeoUtils geoUtils = mock(GeoUtils.class);
        PaymentService paymentService = mock(PaymentService.class);
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        SubscriptionSchedulingService subscriptionSchedulingService = mock(SubscriptionSchedulingService.class);
        AuditService auditService = mock(AuditService.class);

        OrderService service = new OrderService(
                orderRepository,
                zoneRepository,
                geoUtils,
                paymentService,
                subscriptionRepository,
                subscriptionSchedulingService,
                auditService
        );

        User courier = User.builder()
                .id(500L)
                .phone("+79990000500")
                .name("Courier")
                .password("x")
                .userRole(UserRole.COURIER)
                .build();

        OffsetDateTime morning = OffsetDateTime.now().plusDays(1).withHour(8).withMinute(15).withSecond(0).withNano(0);
        OffsetDateTime evening = OffsetDateTime.now().plusDays(1).withHour(19).withMinute(15).withSecond(0).withNano(0);

        Order first = Order.builder()
                .id(20L)
                .status(OrderStatus.PUBLISHED)
                .pickupTime(morning)
                .lat(51.820000)
                .lng(55.170000)
                .build();
        Order second = Order.builder()
                .id(21L)
                .status(OrderStatus.PUBLISHED)
                .pickupTime(evening)
                .lat(51.820100)
                .lng(55.170010)
                .build();

        when(orderRepository.findByIdWithLock(20L)).thenReturn(Optional.of(first));
        when(orderRepository.findByIdWithLock(21L)).thenReturn(Optional.of(second));

        assertThrows(
                IllegalStateException.class,
                () -> service.acceptClusterByCourier(courier, List.of(20L, 21L), 50.0)
        );
    }
}
