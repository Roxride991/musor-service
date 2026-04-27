package com.example.core.service;

import com.example.core.dto.AuditEventResponse;
import com.example.core.dto.CreateSubscriptionRequest;
import com.example.core.dto.ExtendSubscriptionRequest;
import com.example.core.dto.RescheduleSubscriptionRequest;
import com.example.core.dto.SubscriptionResponse;
import com.example.core.dto.UpdateSubscriptionAddressRequest;
import com.example.core.dto.UpdateSubscriptionSlotRequest;
import com.example.core.exception.BadRequestException;
import com.example.core.exception.ConflictException;
import com.example.core.exception.ForbiddenOperationException;
import com.example.core.exception.ResourceNotFoundException;
import com.example.core.mapper.EntityDtoMapper;
import com.example.core.model.NotificationType;
import com.example.core.model.Subscription;
import com.example.core.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionFacadeService {

    private final SubscriptionService subscriptionService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final EntityDtoMapper entityDtoMapper;

    public SubscriptionResponse createSubscription(User currentUser, CreateSubscriptionRequest request) {
        try {
            Subscription subscription = subscriptionService.createSubscription(
                    currentUser,
                    request.getPlan(),
                    request.getAddress(),
                    request.getPickupSlot(),
                    request.getStartDate(),
                    request.getLat(),
                    request.getLng(),
                    request.getCadenceDays()
            );
            notifyUser(
                    currentUser,
                    subscription.getId(),
                    "Подписка оформлена",
                    "Подписка №" + subscription.getId() + " успешно создана",
                    "subscription-created-" + subscription.getId()
            );
            return entityDtoMapper.toSubscriptionResponse(subscription);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage(), ex);
        }
    }

    public List<SubscriptionResponse> getSubscriptions(User currentUser) {
        return subscriptionService.getSubscriptionsForUser(currentUser).stream()
                .map(entityDtoMapper::toSubscriptionResponse)
                .toList();
    }

    public List<SubscriptionResponse> getManageableSubscriptions(User currentUser) {
        return subscriptionService.getActiveSubscriptionsForUser(currentUser).stream()
                .map(entityDtoMapper::toSubscriptionResponse)
                .toList();
    }

    public void cancelSubscription(User currentUser, Long id) {
        try {
            subscriptionService.cancelSubscription(id, currentUser);
            notifyUser(
                    currentUser,
                    id,
                    "Подписка отменена",
                    "Подписка №" + id + " отменена",
                    "subscription-cancel-" + id
            );
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException(ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage(), ex);
        }
    }

    public SubscriptionResponse pauseSubscription(User currentUser, Long id) {
        return executeWithNotification(
                () -> subscriptionService.pauseSubscription(id, currentUser),
                currentUser,
                id,
                "Подписка приостановлена",
                "Подписка №" + id + " поставлена на паузу",
                "subscription-pause-" + id
        );
    }

    public SubscriptionResponse resumeSubscription(User currentUser, Long id) {
        return executeWithNotification(
                () -> subscriptionService.resumeSubscription(id, currentUser),
                currentUser,
                id,
                "Подписка возобновлена",
                "Подписка №" + id + " возобновлена",
                "subscription-resume-" + id
        );
    }

    public SubscriptionResponse skipNextPickup(User currentUser, Long id) {
        return executeWithNotification(
                () -> subscriptionService.skipNextPickup(id, currentUser),
                currentUser,
                id,
                "Вывоз перенесен",
                "Ближайший вывоз подписки №" + id + " перенесен",
                "subscription-skip-" + id
        );
    }

    public SubscriptionResponse updateAddress(User currentUser, Long id, UpdateSubscriptionAddressRequest request) {
        return executeWithNotification(
                () -> subscriptionService.updateAddress(id, currentUser, request.getAddress(), request.getLat(), request.getLng()),
                currentUser,
                id,
                "Адрес обновлен",
                "Адрес подписки №" + id + " успешно обновлен",
                "subscription-address-" + id
        );
    }

    public SubscriptionResponse updateSlot(User currentUser, Long id, UpdateSubscriptionSlotRequest request) {
        return executeWithNotification(
                () -> subscriptionService.updatePickupSlot(id, currentUser, request.getPickupSlot()),
                currentUser,
                id,
                "Слот обновлен",
                "Слот подписки №" + id + " обновлен",
                "subscription-slot-" + id
        );
    }

    public SubscriptionResponse extendSubscription(User currentUser, Long id, ExtendSubscriptionRequest request) {
        return executeWithNotification(
                () -> subscriptionService.extendSubscription(id, currentUser, request == null ? null : request.getPlan()),
                currentUser,
                id,
                "Подписка продлена",
                "Подписка №" + id + " успешно продлена",
                "subscription-extend-" + id
        );
    }

    public SubscriptionResponse rescheduleSubscription(User currentUser, Long id, RescheduleSubscriptionRequest request) {
        return executeWithNotification(
                () -> subscriptionService.rescheduleNextPickup(
                        id,
                        currentUser,
                        request == null ? null : request.getNextPickupDate(),
                        request == null ? null : request.getPickupSlot(),
                        request == null ? null : request.getReason()
                ),
                currentUser,
                id,
                "График обновлен",
                "Ближайший вывоз подписки №" + id + " перенесен",
                "subscription-reschedule-" + id
        );
    }

    public List<AuditEventResponse> getTimeline(User currentUser, Long id, int limit) {
        try {
            Subscription subscription = subscriptionService.getSubscriptionForTimeline(id, currentUser);
            return entityDtoMapper.toAuditEventResponses(
                    auditService.getTimeline("SUBSCRIPTION_ID", String.valueOf(subscription.getId()), limit)
            );
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException(ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ForbiddenOperationException(ex.getMessage());
        }
    }

    private SubscriptionResponse executeWithNotification(
            SubscriptionSupplier supplier,
            User currentUser,
            Long subscriptionId,
            String title,
            String message,
            String dedupeKey
    ) {
        try {
            Subscription subscription = supplier.get();
            notifyUser(currentUser, subscriptionId, title, message, dedupeKey);
            return entityDtoMapper.toSubscriptionResponse(subscription);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage(), ex);
        }
    }

    private void notifyUser(User currentUser, Long subscriptionId, String title, String message, String dedupeKey) {
        notificationService.enqueueInApp(
                currentUser,
                NotificationType.SUBSCRIPTION_UPDATE,
                title,
                message,
                null,
                subscriptionId,
                dedupeKey
        );
    }

    @FunctionalInterface
    private interface SubscriptionSupplier {
        Subscription get();
    }
}
