package com.example.core.service;

import com.example.core.dto.BiOverviewResponse;
import com.example.core.model.OrderStatus;
import com.example.core.model.Payment;
import com.example.core.model.PaymentStatus;
import com.example.core.model.SubscriptionStatus;
import com.example.core.repository.AuditEventRepository;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.PaymentRepository;
import com.example.core.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BiMetricsService {

    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OrderRepository orderRepository;
    private final AuditEventRepository auditEventRepository;

    public BiOverviewResponse buildOverview() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime from30d = now.minusDays(30);

        BigDecimal totalRevenue = nullSafe(paymentRepository.sumSucceededAmount());
        BigDecimal revenueLast30d = nullSafe(paymentRepository.sumSucceededAmountFrom(from30d));

        List<Payment> succeededPayments = paymentRepository.findByStatus(PaymentStatus.SUCCEEDED);
        Set<Long> payingUsers = succeededPayments.stream()
                .map(this::resolvePayerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        BigDecimal ltv = payingUsers.isEmpty()
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(payingUsers.size()), 2, RoundingMode.HALF_UP);

        long activeSubscriptions = subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE)
                + subscriptionRepository.countByStatus(SubscriptionStatus.PAUSED);

        long canceledSubscriptionsLast30d = auditEventRepository
                .countByEventTypeAndCreatedAtAfter("SUBSCRIPTION_CANCEL", from30d);

        double churnRate30d = rate(canceledSubscriptionsLast30d, activeSubscriptions + canceledSubscriptionsLast30d);

        long completedOrders30d = orderRepository.countByStatusInAndPickupTimeBetween(
                List.of(OrderStatus.COMPLETED),
                from30d,
                now
        );
        long cancelledOrders30d = orderRepository.countByStatusInAndPickupTimeBetween(
                List.of(OrderStatus.CANCELLED_BY_CUSTOMER, OrderStatus.CANCELLED_BY_COURIER),
                from30d,
                now
        );

        double completionRate30d = rate(completedOrders30d, completedOrders30d + cancelledOrders30d);

        return BiOverviewResponse.builder()
                .generatedAt(now)
                .totalRevenue(totalRevenue)
                .revenueLast30d(revenueLast30d)
                .ltv(ltv)
                .payingUsers(payingUsers.size())
                .activeSubscriptions(activeSubscriptions)
                .canceledSubscriptionsLast30d(canceledSubscriptionsLast30d)
                .churnRate30d(round4(churnRate30d))
                .completedOrders30d(completedOrders30d)
                .cancelledOrders30d(cancelledOrders30d)
                .completionRate30d(round4(completionRate30d))
                .build();
    }

    private Long resolvePayerId(Payment payment) {
        if (payment == null) {
            return null;
        }

        if (payment.getOrder() != null && payment.getOrder().getClient() != null) {
            return payment.getOrder().getClient().getId();
        }

        if (payment.getSubscription() != null && payment.getSubscription().getUser() != null) {
            return payment.getSubscription().getUser().getId();
        }

        return null;
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0.0;
        }
        return (double) numerator / (double) denominator;
    }

    private double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
