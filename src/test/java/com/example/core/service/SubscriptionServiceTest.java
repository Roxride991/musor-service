package com.example.core.service;

import com.example.core.model.PickupSlot;
import com.example.core.model.Subscription;
import com.example.core.model.SubscriptionPlan;
import com.example.core.model.SubscriptionStatus;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.ServiceZoneRepository;
import com.example.core.repository.SubscriptionRepository;
import com.example.core.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {

    @Test
    void getActiveSubscriptionsForUserShouldIncludePaused() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        SubscriptionService service = new SubscriptionService(
                subscriptionRepository,
                userRepository,
                mock(ServiceZoneRepository.class),
                mock(GeoUtils.class),
                mock(GeocodingService.class),
                mock(SubscriptionSchedulingService.class),
                mock(AuditService.class)
        );

        User user = User.builder()
                .id(1L)
                .phone("+79990000001")
                .name("User")
                .password("secret")
                .userRole(UserRole.CLIENT)
                .build();

        LocalDate today = LocalDate.now();
        Subscription active = Subscription.builder()
                .id(10L)
                .user(user)
                .plan(SubscriptionPlan.MONTHLY)
                .startDate(today.minusDays(5))
                .endDate(today.plusDays(20))
                .price(BigDecimal.valueOf(999))
                .status(SubscriptionStatus.ACTIVE)
                .totalAllowedOrders(10)
                .usedOrders(1)
                .build();

        Subscription paused = Subscription.builder()
                .id(11L)
                .user(user)
                .plan(SubscriptionPlan.WEEKLY)
                .startDate(today.minusDays(20))
                .endDate(today.minusDays(1))
                .price(BigDecimal.valueOf(690))
                .status(SubscriptionStatus.PAUSED)
                .totalAllowedOrders(7)
                .usedOrders(2)
                .build();

        Subscription canceled = Subscription.builder()
                .id(12L)
                .user(user)
                .plan(SubscriptionPlan.MONTHLY)
                .startDate(today.minusDays(40))
                .endDate(today.minusDays(10))
                .price(BigDecimal.valueOf(999))
                .status(SubscriptionStatus.CANCELED)
                .totalAllowedOrders(15)
                .usedOrders(15)
                .build();

        when(subscriptionRepository.findByUserId(user.getId())).thenReturn(List.of(active, paused, canceled));

        List<Subscription> result = service.getActiveSubscriptionsForUser(user);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(s -> s.getId().equals(10L)));
        assertTrue(result.stream().anyMatch(s -> s.getId().equals(11L)));
    }

    @Test
    void pauseSubscriptionShouldFailWhenPauseLimitExhausted() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        SubscriptionSchedulingService schedulingService = mock(SubscriptionSchedulingService.class);
        SubscriptionService service = new SubscriptionService(
                subscriptionRepository,
                userRepository,
                mock(ServiceZoneRepository.class),
                mock(GeoUtils.class),
                mock(GeocodingService.class),
                schedulingService,
                mock(AuditService.class)
        );

        User user = User.builder()
                .id(1L)
                .phone("+79990000001")
                .name("User")
                .password("secret")
                .userRole(UserRole.CLIENT)
                .build();

        LocalDate start = LocalDate.now().minusDays(13);
        LocalDate end = start.plusDays(13); // 14 дней, лимит паузы = floor(14 * 0.15) = 2
        Subscription subscription = Subscription.builder()
                .id(100L)
                .user(user)
                .plan(SubscriptionPlan.WEEKLY)
                .startDate(start)
                .endDate(end)
                .price(BigDecimal.valueOf(690))
                .status(SubscriptionStatus.ACTIVE)
                .totalAllowedOrders(7)
                .usedOrders(1)
                .pausedDaysUsed(2)
                .build();

        when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(subscription));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.pauseSubscription(100L, user)
        );

        assertTrue(ex.getMessage().contains("Лимит паузы исчерпан"));
        verify(schedulingService, never()).cancelUpcomingPublishedOrder(any());
    }

    @Test
    void resumeSubscriptionShouldApplyPauseDaysWithinLimit() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        SubscriptionSchedulingService schedulingService = mock(SubscriptionSchedulingService.class);
        SubscriptionService service = new SubscriptionService(
                subscriptionRepository,
                userRepository,
                mock(ServiceZoneRepository.class),
                mock(GeoUtils.class),
                mock(GeocodingService.class),
                schedulingService,
                mock(AuditService.class)
        );

        User user = User.builder()
                .id(1L)
                .phone("+79990000001")
                .name("User")
                .password("secret")
                .userRole(UserRole.CLIENT)
                .build();

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 30); // 30 дней, лимит паузы = floor(30 * 0.15) = 4
        OffsetDateTime nextPickupAt = OffsetDateTime.now().plusDays(1);
        Subscription subscription = Subscription.builder()
                .id(200L)
                .user(user)
                .plan(SubscriptionPlan.MONTHLY)
                .startDate(start)
                .endDate(end)
                .price(BigDecimal.valueOf(999))
                .status(SubscriptionStatus.PAUSED)
                .totalAllowedOrders(15)
                .usedOrders(3)
                .pickupSlot(PickupSlot.SLOT_8_11)
                .nextPickupAt(nextPickupAt)
                .pausedDaysUsed(1)
                .pauseStartedAt(OffsetDateTime.now().minusDays(100))
                .build();

        when(subscriptionRepository.findById(200L)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Subscription resumed = service.resumeSubscription(200L, user);

        assertEquals(SubscriptionStatus.ACTIVE, resumed.getStatus());
        assertEquals(4, resumed.getPausedDaysUsed());
        assertEquals(end.plusDays(3), resumed.getEndDate());
        assertEquals(nextPickupAt.plusDays(3), resumed.getNextPickupAt());
        assertNull(resumed.getPauseStartedAt());
        verify(schedulingService, times(1)).scheduleNextOrderIfNeeded(200L);
    }

    @Test
    void createSubscriptionShouldRejectWhenPausedExists() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        SubscriptionService service = new SubscriptionService(
                subscriptionRepository,
                userRepository,
                mock(ServiceZoneRepository.class),
                mock(GeoUtils.class),
                mock(GeocodingService.class),
                mock(SubscriptionSchedulingService.class),
                mock(AuditService.class)
        );

        User user = User.builder()
                .id(1L)
                .phone("+79990000001")
                .name("User")
                .password("secret")
                .userRole(UserRole.CLIENT)
                .build();

        Subscription paused = Subscription.builder()
                .id(300L)
                .user(user)
                .plan(SubscriptionPlan.MONTHLY)
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().plusDays(25))
                .price(BigDecimal.valueOf(999))
                .status(SubscriptionStatus.PAUSED)
                .totalAllowedOrders(15)
                .usedOrders(2)
                .build();

        when(subscriptionRepository.findByUserId(user.getId())).thenReturn(List.of(paused));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.createSubscription(
                        user,
                        SubscriptionPlan.WEEKLY,
                        "Оренбург, ул. Тестовая, 1",
                        PickupSlot.SLOT_8_11,
                        LocalDate.now(),
                        51.8,
                        55.1,
                        2
                )
        );

        assertTrue(ex.getMessage().contains("активная или приостановленная"));
    }
}
