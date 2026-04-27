package com.example.core.mapper;

import com.example.core.dto.AuditEventResponse;
import com.example.core.dto.AuthResponse;
import com.example.core.dto.OrderResponse;
import com.example.core.dto.PaymentResponse;
import com.example.core.dto.ServiceZoneResponse;
import com.example.core.dto.SubscriptionResponse;
import com.example.core.dto.UserResponse;
import com.example.core.model.AuditEvent;
import com.example.core.model.Order;
import com.example.core.model.Payment;
import com.example.core.model.ServiceZone;
import com.example.core.model.Subscription;
import com.example.core.model.User;
import com.example.core.repository.PaymentRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class EntityDtoMapper {

    private final PaymentRepository paymentRepository;

    public EntityDtoMapper(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .name(user.getName())
                .role(user.getUserRole())
                .build();
    }

    public AuthResponse toAuthResponse(User user) {
        if (user == null) {
            return null;
        }
        return AuthResponse.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .name(user.getName())
                .role(user.getUserRole())
                .build();
    }

    public OrderResponse toOrderResponse(Order order) {
        return toOrderResponse(order, null);
    }

    public OrderResponse toOrderResponse(Order order, Boolean isAvailable) {
        return toOrderResponse(order, isAvailable, null);
    }

    public List<OrderResponse> toOrderResponses(List<Order> orders) {
        return toOrderResponses(orders, null);
    }

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

        BigDecimal price;
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
                .lat(resolveOrderLat(order))
                .lng(resolveOrderLng(order))
                .status(order.getStatus())
                .price(price)
                .createdAt(order.getCreatedAt())
                .clientName(order.getClient() != null ? order.getClient().getName() : null)
                .isAvailable(isAvailable);

        if (order.getCourier() != null) {
            builder.courierName(order.getCourier().getName());
        }

        return builder.build();
    }

    public PaymentResponse toPaymentResponse(Payment payment) {
        if (payment == null) {
            return null;
        }

        return PaymentResponse.builder()
                .id(payment.getId())
                .type(payment.getType())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .provider(payment.getProvider())
                .externalId(payment.getExternalId())
                .confirmationUrl(payment.getConfirmationUrl())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    public SubscriptionResponse toSubscriptionResponse(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getPlan(),
                subscription.getPlan().getDescription(),
                subscription.getTotalAllowedOrders(),
                subscription.getUsedOrders(),
                subscription.getRemainingOrders(),
                subscription.getServiceAddress(),
                subscription.getPickupSlot(),
                subscription.getCadenceDays(),
                subscription.getNextPickupAt(),
                subscription.getStartDate(),
                subscription.getEndDate(),
                subscription.getPrice(),
                subscription.getStatus(),
                subscription.getCreatedAt()
        );
    }

    public ServiceZoneResponse toServiceZoneResponse(ServiceZone zone) {
        if (zone == null) {
            return null;
        }

        return ServiceZoneResponse.builder()
                .id(zone.getId())
                .name(zone.getName())
                .active(zone.isActive())
                .createdAt(zone.getCreatedAt())
                .coordinates(zone.getCoordinates() == null
                        ? List.of()
                        : zone.getCoordinates().stream()
                                .map(coordinate -> ServiceZoneResponse.CoordinateResponse.builder()
                                        .lat(coordinate.getLat())
                                        .lng(coordinate.getLng())
                                        .build())
                                .toList())
                .build();
    }

    public AuditEventResponse toAuditEventResponse(AuditEvent event) {
        if (event == null) {
            return null;
        }
        return AuditEventResponse.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .outcome(event.getOutcome())
                .actorUserId(event.getActorUserId())
                .actorRole(event.getActorRole())
                .targetType(event.getTargetType())
                .targetId(event.getTargetId())
                .clientIp(event.getClientIp())
                .details(event.getDetails())
                .createdAt(event.getCreatedAt())
                .build();
    }

    public List<AuditEventResponse> toAuditEventResponses(List<AuditEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        return events.stream()
                .map(this::toAuditEventResponse)
                .toList();
    }

    private Double resolveOrderLat(Order order) {
        if (order == null) {
            return null;
        }
        if (order.getLat() != null) {
            return order.getLat();
        }
        if (order.getSubscription() != null) {
            return order.getSubscription().getServiceLat();
        }
        return null;
    }

    private Double resolveOrderLng(Order order) {
        if (order == null) {
            return null;
        }
        if (order.getLng() != null) {
            return order.getLng();
        }
        if (order.getSubscription() != null) {
            return order.getSubscription().getServiceLng();
        }
        return null;
    }
}
