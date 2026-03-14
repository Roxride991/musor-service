package com.example.core.service;

import com.example.core.dto.CourierWorkloadResponse;
import com.example.core.dto.DispatchRecommendationResponse;
import com.example.core.dto.OperatorDashboardResponse;
import com.example.core.model.Order;
import com.example.core.model.OrderStatus;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.monitoring.FlowMetricsService;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OperatorDashboardService {

    private static final List<OrderStatus> OPEN_STATUSES = List.of(
            OrderStatus.PUBLISHED,
            OrderStatus.ACCEPTED,
            OrderStatus.ON_THE_WAY,
            OrderStatus.PICKED_UP
    );

    private static final List<OrderStatus> TERMINAL_STATUSES = List.of(
            OrderStatus.COMPLETED,
            OrderStatus.CANCELLED_BY_CUSTOMER,
            OrderStatus.CANCELLED_BY_COURIER
    );

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final FlowMetricsService flowMetricsService;

    public OperatorDashboardResponse buildDashboard() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime dayAgo = now.minusHours(24);
        OffsetDateTime nextHour = now.plusHours(1);
        Duration metricsWindow = Duration.ofMinutes(15);

        long publishedCount = orderRepository.countByStatus(OrderStatus.PUBLISHED);
        long acceptedCount = orderRepository.countByStatus(OrderStatus.ACCEPTED);
        long onTheWayCount = orderRepository.countByStatus(OrderStatus.ON_THE_WAY);
        long pickedUpCount = orderRepository.countByStatus(OrderStatus.PICKED_UP);
        long completedLast24h = orderRepository.countByStatusInAndPickupTimeBetween(
                List.of(OrderStatus.COMPLETED), dayAgo, now
        );
        long cancelledLast24h = orderRepository.countByStatusInAndPickupTimeBetween(
                List.of(OrderStatus.CANCELLED_BY_CUSTOMER, OrderStatus.CANCELLED_BY_COURIER), dayAgo, now
        );
        long overdueOpenCount = orderRepository.countByStatusInAndPickupTimeBefore(OPEN_STATUSES, now);
        long dueNextHourCount = orderRepository.countByStatusInAndPickupTimeBetween(OPEN_STATUSES, now, nextHour);
        long terminalIn24h = orderRepository.countByStatusInAndPickupTimeBetween(TERMINAL_STATUSES, dayAgo, now);

        double completionRate24h = terminalIn24h == 0
                ? 0.0
                : (double) completedLast24h / terminalIn24h;

        List<CourierWorkloadResponse> workloads = userRepository.findAllByRole(UserRole.COURIER).stream()
                .filter(courier -> !courier.isBanned())
                .map(this::toCourierWorkload)
                .sorted(Comparator.comparingInt(CourierWorkloadResponse::getActiveOrders))
                .toList();

        return OperatorDashboardResponse.builder()
                .generatedAt(now)
                .publishedCount(publishedCount)
                .acceptedCount(acceptedCount)
                .onTheWayCount(onTheWayCount)
                .pickedUpCount(pickedUpCount)
                .completedLast24h(completedLast24h)
                .cancelledLast24h(cancelledLast24h)
                .overdueOpenCount(overdueOpenCount)
                .dueNextHourCount(dueNextHourCount)
                .completionRate24h(round4(completionRate24h))
                .authFailureRate15m(round4(flowMetricsService.authFailureRate(metricsWindow)))
                .orderCreateFailureRate15m(round4(flowMetricsService.orderCreateFailureRate(metricsWindow)))
                .telegramVerifyFailureRate15m(round4(flowMetricsService.telegramVerifyFailureRate(metricsWindow)))
                .courierWorkloads(workloads)
                .build();
    }

    public List<DispatchRecommendationResponse> buildDispatchRecommendations(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<Order> publishedOrders = orderRepository.findByStatusAndCourierIsNull(OrderStatus.PUBLISHED).stream()
                .sorted(Comparator.comparing(Order::getPickupTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(safeLimit)
                .toList();

        List<User> couriers = userRepository.findAllByRole(UserRole.COURIER).stream()
                .filter(courier -> !courier.isBanned())
                .filter(courier -> courier.getId() != null)
                .toList();

        if (publishedOrders.isEmpty()) {
            return List.of();
        }

        if (couriers.isEmpty()) {
            return publishedOrders.stream()
                    .map(order -> DispatchRecommendationResponse.builder()
                            .orderId(order.getId())
                            .orderAddress(order.getAddress())
                            .pickupTime(order.getPickupTime())
                            .recommended(false)
                            .reason("Нет доступных курьеров")
                            .build())
                    .toList();
        }

        Map<Long, Integer> virtualWorkload = new HashMap<>();
        for (User courier : couriers) {
            virtualWorkload.put(courier.getId(), countActiveOrders(courier));
        }

        return publishedOrders.stream()
                .map(order -> recommend(order, couriers, virtualWorkload))
                .toList();
    }

    private CourierWorkloadResponse toCourierWorkload(User courier) {
        int activeOrders = countActiveOrders(courier);

        return CourierWorkloadResponse.builder()
                .courierId(courier.getId())
                .courierName(courier.getName())
                .courierPhoneMasked(maskPhone(courier.getPhone()))
                .activeOrders(activeOrders)
                .build();
    }

    private DispatchRecommendationResponse recommend(
            Order order,
            List<User> couriers,
            Map<Long, Integer> virtualWorkload
    ) {
        User selectedCourier = couriers.stream()
                .min(Comparator
                        .comparingInt((User courier) -> virtualWorkload.getOrDefault(courier.getId(), Integer.MAX_VALUE))
                        .thenComparing(courier -> Objects.toString(courier.getName(), ""))
                )
                .orElse(null);

        if (selectedCourier == null) {
            return DispatchRecommendationResponse.builder()
                    .orderId(order.getId())
                    .orderAddress(order.getAddress())
                    .pickupTime(order.getPickupTime())
                    .recommended(false)
                    .reason("Не удалось подобрать курьера")
                    .build();
        }

        int activeOrders = virtualWorkload.getOrDefault(selectedCourier.getId(), 0);
        virtualWorkload.put(selectedCourier.getId(), activeOrders + 1);

        return DispatchRecommendationResponse.builder()
                .orderId(order.getId())
                .orderAddress(order.getAddress())
                .pickupTime(order.getPickupTime())
                .courierId(selectedCourier.getId())
                .courierName(selectedCourier.getName())
                .courierPhoneMasked(maskPhone(selectedCourier.getPhone()))
                .activeOrders(activeOrders)
                .recommended(true)
                .reason("Минимальная текущая нагрузка")
                .build();
    }

    private int countActiveOrders(User courier) {
        return (int) orderRepository.countByCourierAndStatusIn(courier, OPEN_STATUSES);
    }

    private String maskPhone(String phone) {
        if (phone == null || !phone.matches("^\\+7\\d{10}$")) {
            return "+7*** ***-**-**";
        }
        return "+7*** ***-**-" + phone.substring(phone.length() - 2);
    }

    private double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
