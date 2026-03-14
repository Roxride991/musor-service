package com.example.core.service;

import com.example.core.model.Order;
import com.example.core.model.OrderStatus;
import com.example.core.model.PickupSlot;
import com.example.core.model.Subscription;
import com.example.core.model.SubscriptionStatus;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionSchedulingService {

    private static final List<OrderStatus> OPEN_ORDER_STATUSES = List.of(
            OrderStatus.PUBLISHED,
            OrderStatus.ACCEPTED,
            OrderStatus.ON_THE_WAY,
            OrderStatus.PICKED_UP
    );

    private final SubscriptionRepository subscriptionRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public boolean scheduleNextOrderIfNeeded(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findByIdWithLock(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));
        return scheduleNextOrderIfNeeded(subscription, OffsetDateTime.now());
    }

    @Transactional
    public boolean cancelUpcomingPublishedOrder(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findByIdWithLock(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));
        return cancelUpcomingPublishedOrderInternal(subscription) != null;
    }

    @Transactional
    public OffsetDateTime cancelUpcomingPublishedOrderAndGetPickupTime(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findByIdWithLock(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));
        return cancelUpcomingPublishedOrderInternal(subscription);
    }

    @Transactional
    public Subscription skipNextPickup(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findByIdWithLock(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Пропустить можно только для активной подписки");
        }

        OffsetDateTime cancelledPickupTime = cancelUpcomingPublishedOrderInternal(subscription);
        boolean cancelledCurrentOrder = cancelledPickupTime != null;
        if (!cancelledCurrentOrder) {
            OffsetDateTime base = subscription.getNextPickupAt();
            if (base == null) {
                base = calculateInitialCandidate(subscription, OffsetDateTime.now().plusHours(1));
            }
            if (base != null) {
                subscription.setNextPickupAt(base.plusDays(getCadenceDays(subscription)));
            }
            subscriptionRepository.save(subscription);
        }

        scheduleNextOrderIfNeeded(subscription, OffsetDateTime.now());
        return subscription;
    }

    @Transactional
    public Subscription scheduleAfterManualSubscriptionChange(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findByIdWithLock(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));
        scheduleNextOrderIfNeeded(subscription, OffsetDateTime.now());
        return subscription;
    }

    public int calculateTotalAllowedOrders(LocalDate startDate, LocalDate endDate, int cadenceDays) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return 0;
        }

        int safeCadence = Math.max(1, cadenceDays);
        long totalDaysInclusive = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return (int) ((totalDaysInclusive + safeCadence - 1) / safeCadence);
    }

    private boolean scheduleNextOrderIfNeeded(Subscription subscription, OffsetDateTime now) {
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            return false;
        }

        if (subscription.getEndDate() == null || subscription.getStartDate() == null ||
                subscription.getPickupSlot() == null || !hasText(subscription.getServiceAddress())) {
            return false;
        }

        if (subscription.getEndDate().isBefore(now.toLocalDate())) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            return false;
        }

        if (subscription.getUsedOrders() >= subscription.getTotalAllowedOrders()) {
            if (!hasOpenOrder(subscription.getId())) {
                subscription.setStatus(SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(subscription);
            }
            return false;
        }

        if (hasOpenOrder(subscription.getId())) {
            return false;
        }

        OffsetDateTime minAllowed = now.plusHours(1);
        OffsetDateTime candidate = resolveCandidate(subscription, minAllowed);
        if (candidate == null) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            return false;
        }

        if (subscription.getUsedOrders() + 1 > subscription.getTotalAllowedOrders()) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            return false;
        }

        Order order = Order.builder()
                .client(subscription.getUser())
                .subscription(subscription)
                .address(subscription.getServiceAddress())
                .pickupTime(candidate)
                .comment("Автозаказ по подписке")
                .status(OrderStatus.PUBLISHED)
                .build();
        orderRepository.save(order);

        subscription.setUsedOrders(subscription.getUsedOrders() + 1);
        subscription.setNextPickupAt(candidate.plusDays(getCadenceDays(subscription)));
        subscriptionRepository.save(subscription);
        return true;
    }

    private OffsetDateTime resolveCandidate(Subscription subscription, OffsetDateTime minAllowed) {
        OffsetDateTime candidate = subscription.getNextPickupAt();
        if (candidate == null) {
            candidate = calculateInitialCandidate(subscription, minAllowed);
        } else {
            candidate = alignCandidateToSlot(candidate, subscription.getPickupSlot());
        }

        if (candidate == null) {
            return null;
        }

        while (candidate.isBefore(minAllowed)) {
            candidate = candidate.plusDays(getCadenceDays(subscription));
        }

        if (candidate.toLocalDate().isAfter(subscription.getEndDate())) {
            return null;
        }

        return candidate;
    }

    private OffsetDateTime calculateInitialCandidate(Subscription subscription, OffsetDateTime minAllowed) {
        LocalDate startDate = subscription.getStartDate();
        if (startDate == null || subscription.getPickupSlot() == null) {
            return null;
        }

        LocalDate candidateDate = startDate.isAfter(minAllowed.toLocalDate()) ? startDate : minAllowed.toLocalDate();
        OffsetDateTime candidate = toOffset(candidateDate, subscription.getPickupSlot());

        while (candidate.isBefore(minAllowed)) {
            candidate = candidate.plusDays(getCadenceDays(subscription));
        }
        return candidate;
    }

    private OffsetDateTime alignCandidateToSlot(OffsetDateTime value, PickupSlot pickupSlot) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate localDate = value.atZoneSameInstant(zoneId).toLocalDate();
        return toOffset(localDate, pickupSlot);
    }

    private OffsetDateTime toOffset(LocalDate localDate, PickupSlot pickupSlot) {
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDate, pickupSlot.getStartTime(), zoneId);
        return zonedDateTime.toOffsetDateTime();
    }

    private OffsetDateTime cancelUpcomingPublishedOrderInternal(Subscription subscription) {
        return orderRepository.findFirstBySubscriptionIdAndStatusOrderByPickupTimeAsc(
                        subscription.getId(), OrderStatus.PUBLISHED)
                .map(order -> {
                    OffsetDateTime pickupTime = order.getPickupTime();
                    order.setStatus(OrderStatus.CANCELLED_BY_CUSTOMER);
                    orderRepository.save(order);

                    if (subscription.getUsedOrders() > 0) {
                        subscription.setUsedOrders(subscription.getUsedOrders() - 1);
                    }
                    subscriptionRepository.save(subscription);
                    return pickupTime;
                })
                .orElse(null);
    }

    private boolean hasOpenOrder(Long subscriptionId) {
        return orderRepository.existsBySubscriptionIdAndStatusIn(subscriptionId, OPEN_ORDER_STATUSES);
    }

    private int getCadenceDays(Subscription subscription) {
        return Math.max(1, subscription.getCadenceDays());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
