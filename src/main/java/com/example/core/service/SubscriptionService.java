package com.example.core.service;

import com.example.core.model.*;
import com.example.core.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Сервис подписок: создание, получение, отмена, пауза и возобновление.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    /** Создаёт новую подписку пользователю по выбранному плану. */
    // com.example.core.service.SubscriptionService (обновлённый метод createSubscription)

    @Transactional
    public Subscription createSubscription(User user, SubscriptionPlan plan) {
        if (user.getUserRole() != UserRole.CLIENT) {
            throw new IllegalStateException("Только клиенты могут оформлять подписки");
        }

        LocalDate now = LocalDate.now();
        boolean hasActive = subscriptionRepository.findByUserId(user.getId()).stream()
                .anyMatch(s -> s.getStatus() == SubscriptionStatus.ACTIVE && !s.getEndDate().isBefore(now));
        if (hasActive) {
            throw new IllegalStateException("У вас уже есть активная подписка");
        }

        LocalDate startDate = now;
        LocalDate endDate = switch (plan) {
            case WEEKLY -> startDate.plusWeeks(1);
            case MONTHLY -> startDate.plusMonths(1);
        };

        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(plan)
                .startDate(startDate)
                .endDate(endDate)
                .price(plan.getPrice())
                .status(SubscriptionStatus.ACTIVE)
                .totalAllowedOrders(plan.getTotalOrders())
                .usedOrders(0)
                .build();

        return subscriptionRepository.save(subscription);
    }

    /** Возвращает все подписки пользователя. */
    public List<Subscription> getSubscriptionsForUser(User user) {
        return subscriptionRepository.findByUserId(user.getId());
    }

    /** Возвращает только активные подписки пользователя (статус ACTIVE и не истекшие). */
    public List<Subscription> getActiveSubscriptionsForUser(User user) {
        LocalDate now = LocalDate.now();
        return subscriptionRepository.findByUserId(user.getId()).stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .filter(s -> !s.getEndDate().isBefore(now))
                .toList();
    }

    /** Отменяет активную подписку (статус CANCELED). */
    @Transactional
    public void cancelSubscription(Long subscriptionId, User currentUser) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));

        if (!subscription.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("Подписка не принадлежит вам");
        }
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Можно отменить только активную подписку");
        }

        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscriptionRepository.save(subscription);
    }

    /** Приостанавливает активную подписку (статус PAUSED). */
    @Transactional
    public Subscription pauseSubscription(Long subscriptionId, User currentUser) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));

        if (!subscription.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("Подписка не принадлежит вам");
        }
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Можно приостановить только активную подписку");
        }

        subscription.setStatus(SubscriptionStatus.PAUSED);
        return subscriptionRepository.save(subscription);
    }

    /** Возобновляет приостановленную подписку (статус ACTIVE). */
    @Transactional
    public Subscription resumeSubscription(Long subscriptionId, User currentUser) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));

        if (!subscription.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("Подписка не принадлежит вам");
        }
        if (subscription.getStatus() != SubscriptionStatus.PAUSED) {
            throw new IllegalStateException("Можно возобновить только приостановленную подписку");
        }

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        return subscriptionRepository.save(subscription);
    }
}