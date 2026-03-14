package com.example.core.service;

import com.example.core.dto.DispatchRecommendationResponse;
import com.example.core.dto.OperatorDashboardResponse;
import com.example.core.model.Order;
import com.example.core.model.OrderStatus;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.monitoring.FlowMetricsService;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperatorDashboardServiceTest {

    @Test
    void buildDispatchRecommendationsShouldPickLeastLoadedCourier() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        FlowMetricsService flowMetricsService = mock(FlowMetricsService.class);

        OperatorDashboardService service = new OperatorDashboardService(
                orderRepository,
                userRepository,
                flowMetricsService
        );

        User courier1 = User.builder()
                .id(10L)
                .name("Courier 1")
                .phone("+79990000010")
                .userRole(UserRole.COURIER)
                .build();

        User courier2 = User.builder()
                .id(11L)
                .name("Courier 2")
                .phone("+79990000011")
                .userRole(UserRole.COURIER)
                .build();

        Order order1 = Order.builder()
                .id(100L)
                .address("ул. Тестовая, 1")
                .status(OrderStatus.PUBLISHED)
                .pickupTime(OffsetDateTime.now().plusHours(2))
                .build();

        Order order2 = Order.builder()
                .id(101L)
                .address("ул. Тестовая, 2")
                .status(OrderStatus.PUBLISHED)
                .pickupTime(OffsetDateTime.now().plusHours(3))
                .build();

        when(orderRepository.findByStatusAndCourierIsNull(OrderStatus.PUBLISHED)).thenReturn(List.of(order1, order2));
        when(userRepository.findAllByRole(UserRole.COURIER)).thenReturn(List.of(courier1, courier2));
        when(orderRepository.countByCourierAndStatusIn(courier1, List.of(
                OrderStatus.PUBLISHED,
                OrderStatus.ACCEPTED,
                OrderStatus.ON_THE_WAY,
                OrderStatus.PICKED_UP
        ))).thenReturn(3L);
        when(orderRepository.countByCourierAndStatusIn(courier2, List.of(
                OrderStatus.PUBLISHED,
                OrderStatus.ACCEPTED,
                OrderStatus.ON_THE_WAY,
                OrderStatus.PICKED_UP
        ))).thenReturn(1L);

        List<DispatchRecommendationResponse> result = service.buildDispatchRecommendations(5);

        assertEquals(2, result.size());
        assertEquals(11L, result.get(0).getCourierId());
        assertTrue(result.get(0).isRecommended());
        assertEquals(1, result.get(0).getActiveOrders());
    }

    @Test
    void buildDashboardShouldCalculateCoreMetrics() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        FlowMetricsService flowMetricsService = mock(FlowMetricsService.class);

        OperatorDashboardService service = new OperatorDashboardService(
                orderRepository,
                userRepository,
                flowMetricsService
        );

        User courier = User.builder()
                .id(15L)
                .name("Courier")
                .phone("+79990000015")
                .userRole(UserRole.COURIER)
                .build();

        when(orderRepository.countByStatus(OrderStatus.PUBLISHED)).thenReturn(5L);
        when(orderRepository.countByStatus(OrderStatus.ACCEPTED)).thenReturn(4L);
        when(orderRepository.countByStatus(OrderStatus.ON_THE_WAY)).thenReturn(3L);
        when(orderRepository.countByStatus(OrderStatus.PICKED_UP)).thenReturn(2L);
        when(orderRepository.countByStatusInAndPickupTimeBetween(anyList(), any(), any())).thenReturn(10L);
        when(orderRepository.countByStatusInAndPickupTimeBefore(anyList(), any())).thenReturn(1L);
        when(userRepository.findAllByRole(UserRole.COURIER)).thenReturn(List.of(courier));
        when(orderRepository.countByCourierAndStatusIn(courier, List.of(
                OrderStatus.PUBLISHED,
                OrderStatus.ACCEPTED,
                OrderStatus.ON_THE_WAY,
                OrderStatus.PICKED_UP
        ))).thenReturn(2L);
        when(flowMetricsService.authFailureRate(any())).thenReturn(0.1);
        when(flowMetricsService.orderCreateFailureRate(any())).thenReturn(0.2);
        when(flowMetricsService.telegramVerifyFailureRate(any())).thenReturn(0.3);

        OperatorDashboardResponse response = service.buildDashboard();

        assertEquals(5L, response.getPublishedCount());
        assertEquals(4L, response.getAcceptedCount());
        assertEquals(0.1, response.getAuthFailureRate15m());
        assertEquals(1, response.getCourierWorkloads().size());
    }
}
