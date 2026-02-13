package com.example.core.dto.mapper;

import com.example.core.dto.*;
import com.example.core.model.Order;
import com.example.core.model.Payment;
import com.example.core.model.Subscription;
import com.example.core.model.User;
import com.example.core.repository.PaymentRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Маппер для преобразования Entity в DTO.
 */
/**
 * Маппер между сущностями домена и DTO ответов API.
 */
@Component
public class DtoMapper {

    private final PaymentRepository paymentRepository;

    public DtoMapper(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    // ======================
    // User маппинг
    // ======================

    /** Преобразует пользователя в безопасный ответ (без чувствительных данных). */
    public UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .name(user.getName())
                .role(user.getUserRole())
                // createdAt убран — не нужен во фронтенде
                .build();
    }

    /** Преобразует пользователя в ответ аутентификации. */
    public AuthResponse toAuthResponse(User user) {
        if (user == null) {
            return null;
        }
        return AuthResponse.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .name(user.getName())
                .role(user.getUserRole())
                // createdAt убран — не нужен во фронтенде
                .build();
    }

    // ======================
    // Order маппинг
    // ======================

    /** Преобразует заказ в DTO для списка/деталей. */
    public OrderResponse toOrderResponse(Order order) {
        return toOrderResponse(order, null);
    }

    /** Преобразует заказ в DTO с указанием, является ли он доступным (для курьера). */
    public OrderResponse toOrderResponse(Order order, Boolean isAvailable) {
        return toOrderResponse(order, isAvailable, null);
    }

    /** Пакетное преобразование заказов в DTO с одним запросом к платежам. */
    public List<OrderResponse> toOrderResponses(List<Order> orders) {
        return toOrderResponses(orders, null);
    }

    /** Пакетное преобразование заказов в DTO с одним запросом к платежам. */
    public List<OrderResponse> toOrderResponses(List<Order> orders, Boolean isAvailable) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, BigDecimal> priceByOrderId = new HashMap<>();
        if (!orderIds.isEmpty()) {
            for (Payment payment : paymentRepository.findByOrderIdIn(orderIds)) {
                if (payment != null && payment.getOrder() != null && payment.getOrder().getId() != null) {
                    priceByOrderId.putIfAbsent(payment.getOrder().getId(), payment.getAmount());
                }
            }
        }

        return orders.stream()
                .map(order -> toOrderResponse(order, isAvailable, priceByOrderId))
                .toList();
    }

    private OrderResponse toOrderResponse(Order order, Boolean isAvailable, Map<Long, BigDecimal> priceByOrderId) {
        if (order == null) {
            return null;
        }

        BigDecimal price = null;
        if (priceByOrderId != null) {
            price = priceByOrderId.get(order.getId());
        } else {
            price = paymentRepository.findByOrderId(order.getId()).stream()
                    .findFirst()
                    .map(Payment::getAmount)
                    .orElse(null);
        }

        OrderResponse.OrderResponseBuilder builder = OrderResponse.builder()
                .id(order.getId())
                .address(order.getAddress())
                .pickupTime(order.getPickupTime())
                .comment(order.getComment())
                .status(order.getStatus())
                .price(price)
                .createdAt(order.getCreatedAt()) // оставляем, если используется во фронтенде
                .clientName(order.getClient() != null ? order.getClient().getName() : null)
                .isAvailable(isAvailable); // Устанавливаем флаг доступности

        // Курьер может быть null
        if (order.getCourier() != null) {
            builder.courierName(order.getCourier().getName());
        }

        return builder.build();
    }

    // ======================
    // Payment маппинг
    // ======================

    /** Преобразует платеж в DTO ответа. */
    public PaymentResponse toPaymentResponse(Payment payment) {
        if (payment == null) {
            return null;
        }

        return PaymentResponse.builder()
                .id(payment.getId())
                .type(payment.getType())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .externalId(payment.getExternalId())
                .createdAt(payment.getCreatedAt())
                // orderId и subscriptionId убраны — не нужны во фронтенде
                .build();
    }

    // ======================
    // Subscription маппинг
    // ======================

    /** Преобразует подписку в DTO ответа. */
    public SubscriptionResponse toSubscriptionResponse(Subscription s) {
        return new SubscriptionResponse(
                s.getId(),
                s.getPlan(),
                s.getPlan().getDescription(), // ← Вот здесь!
                s.getTotalAllowedOrders(),
                s.getUsedOrders(),
                s.getRemainingOrders(),
                s.getStartDate(),
                s.getEndDate(),
                s.getPrice(),
                s.getStatus(),
                s.getCreatedAt()
        );
    }
}
