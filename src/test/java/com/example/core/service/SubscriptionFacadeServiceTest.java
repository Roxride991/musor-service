package com.example.core.service;

import com.example.core.dto.CreateSubscriptionRequest;
import com.example.core.dto.SubscriptionResponse;
import com.example.core.mapper.EntityDtoMapper;
import com.example.core.model.PickupSlot;
import com.example.core.model.Subscription;
import com.example.core.model.SubscriptionPlan;
import com.example.core.model.SubscriptionStatus;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionFacadeServiceTest {

    @Test
    void createSubscriptionShouldDelegateToServiceAndSendNotification() {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AuditService auditService = mock(AuditService.class);
        EntityDtoMapper entityDtoMapper = mock(EntityDtoMapper.class);

        SubscriptionFacadeService facadeService = new SubscriptionFacadeService(
                subscriptionService,
                notificationService,
                auditService,
                entityDtoMapper
        );

        User user = User.builder()
                .id(1L)
                .userRole(UserRole.CLIENT)
                .phone("+79990000000")
                .password("x")
                .name("Client")
                .build();

        CreateSubscriptionRequest request = new CreateSubscriptionRequest();
        request.setPlan(SubscriptionPlan.MONTHLY);
        request.setAddress("ул. Ленина, 1");
        request.setPickupSlot(PickupSlot.SLOT_8_11);
        request.setStartDate(LocalDate.now().plusDays(1));
        request.setCadenceDays(2);

        Subscription subscription = Subscription.builder()
                .id(15L)
                .user(user)
                .plan(SubscriptionPlan.MONTHLY)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(30))
                .price(BigDecimal.valueOf(1990))
                .pickupSlot(PickupSlot.SLOT_8_11)
                .status(SubscriptionStatus.ACTIVE)
                .totalAllowedOrders(15)
                .usedOrders(0)
                .createdAt(OffsetDateTime.now())
                .build();

        SubscriptionResponse response = new SubscriptionResponse(
                15L,
                SubscriptionPlan.MONTHLY,
                "desc",
                15,
                0,
                15,
                "ул. Ленина, 1",
                PickupSlot.SLOT_8_11,
                2,
                null,
                subscription.getStartDate(),
                subscription.getEndDate(),
                BigDecimal.valueOf(1990),
                SubscriptionStatus.ACTIVE,
                subscription.getCreatedAt()
        );

        when(subscriptionService.createSubscription(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(subscription);
        when(entityDtoMapper.toSubscriptionResponse(subscription)).thenReturn(response);

        SubscriptionResponse actual = facadeService.createSubscription(user, request);

        assertEquals(response, actual);
        verify(notificationService, times(1))
                .enqueueInApp(any(), any(), any(), any(), any(), any(), any());
    }
}
