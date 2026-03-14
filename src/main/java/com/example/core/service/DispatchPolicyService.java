package com.example.core.service;

import com.example.core.dto.DispatchPolicyResponse;
import com.example.core.dto.DispatchRecommendationResponse;
import com.example.core.model.DispatchPolicyMode;
import com.example.core.model.NotificationType;
import com.example.core.model.Order;
import com.example.core.model.OrderStatus;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchPolicyService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OperatorDashboardService operatorDashboardService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Value("${dispatch.mode:MANUAL}")
    private String dispatchModeRaw;

    @Value("${dispatch.auto.max-assignments-per-run:20}")
    private int maxAssignmentsPerRun;

    @Value("${dispatch.hybrid.lookahead-minutes:120}")
    private int hybridLookaheadMinutes;

    @Value("${dispatch.enabled:true}")
    private boolean dispatchEnabled;

    @Transactional(readOnly = true)
    public DispatchPolicyResponse getPolicy() {
        return DispatchPolicyResponse.builder()
                .mode(getDispatchMode())
                .enabled(dispatchEnabled)
                .maxAssignmentsPerRun(maxAssignmentsPerRun)
                .hybridLookaheadMinutes(hybridLookaheadMinutes)
                .build();
    }

    @Transactional
    public Order assignOrderManually(Long orderId, Long courierId, User admin, String reason) {
        if (admin.getUserRole() != UserRole.ADMIN) {
            throw new IllegalStateException("Только администратор может назначать курьера вручную");
        }
        return assignOrder(orderId, courierId, admin, reason == null ? "manual-assignment" : reason);
    }

    @Scheduled(fixedDelayString = "${dispatch.auto.period-ms:60000}")
    @Transactional
    public void autoDispatchTick() {
        if (!dispatchEnabled) {
            return;
        }

        DispatchPolicyMode mode = getDispatchMode();
        if (mode == DispatchPolicyMode.MANUAL) {
            return;
        }

        int limit = Math.max(1, Math.min(maxAssignmentsPerRun, 100));
        List<DispatchRecommendationResponse> recommendations = operatorDashboardService.buildDispatchRecommendations(limit);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime hybridLimit = now.plusMinutes(Math.max(30, hybridLookaheadMinutes));

        for (DispatchRecommendationResponse recommendation : recommendations) {
            if (!recommendation.isRecommended()) {
                continue;
            }
            if (recommendation.getOrderId() == null || recommendation.getCourierId() == null) {
                continue;
            }

            if (mode == DispatchPolicyMode.HYBRID && recommendation.getPickupTime() != null) {
                boolean shouldAssign = recommendation.getPickupTime().isBefore(hybridLimit) || recommendation.getPickupTime().isEqual(hybridLimit);
                if (!shouldAssign) {
                    continue;
                }
            }

            try {
                assignOrder(
                        recommendation.getOrderId(),
                        recommendation.getCourierId(),
                        null,
                        mode == DispatchPolicyMode.AUTO ? "auto-dispatch" : "hybrid-dispatch"
                );
            } catch (Exception e) {
                log.warn("Auto dispatch skipped for order {}: {}", recommendation.getOrderId(), e.getMessage());
            }
        }
    }

    private Order assignOrder(Long orderId, Long courierId, User actor, String reason) {
        User courier = userRepository.findById(courierId)
                .orElseThrow(() -> new IllegalArgumentException("Курьер не найден"));
        if (courier.getUserRole() != UserRole.COURIER || courier.isBanned()) {
            throw new IllegalStateException("Указанный пользователь не может быть назначен курьером");
        }

        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));

        if (order.getStatus() != OrderStatus.PUBLISHED || order.getCourier() != null) {
            return order;
        }

        order.setCourier(courier);
        order.setStatus(OrderStatus.ACCEPTED);
        Order saved = orderRepository.save(order);

        auditService.log(
                "ORDER_DISPATCH",
                "SUCCESS",
                actor == null ? null : actor.getId(),
                actor == null ? "SYSTEM" : actor.getUserRole().name(),
                "ORDER_ID",
                String.valueOf(saved.getId()),
                "Assigned courierId=" + courierId + ", reason=" + reason,
                null
        );

        if (saved.getClient() != null) {
            notificationService.enqueueInApp(
                    saved.getClient(),
                    NotificationType.ORDER_STATUS,
                    "Курьер назначен",
                    "Для заказа №" + saved.getId() + " назначен курьер",
                    saved.getId(),
                    null,
                    "dispatch-client-" + saved.getId() + "-" + courierId
            );
        }
        notificationService.enqueueInApp(
                courier,
                NotificationType.ORDER_STATUS,
                "Вам назначен заказ",
                "Назначен заказ №" + saved.getId(),
                saved.getId(),
                null,
                "dispatch-courier-" + saved.getId() + "-" + courierId
        );

        return saved;
    }

    private DispatchPolicyMode getDispatchMode() {
        if (dispatchModeRaw == null || dispatchModeRaw.isBlank()) {
            return DispatchPolicyMode.MANUAL;
        }
        try {
            return DispatchPolicyMode.valueOf(dispatchModeRaw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return DispatchPolicyMode.MANUAL;
        }
    }
}
