package com.example.core.controller;

import com.example.core.dto.CreateSubscriptionRequest;
import com.example.core.dto.AuditEventResponse;
import com.example.core.dto.ExtendSubscriptionRequest;
import com.example.core.dto.RescheduleSubscriptionRequest;
import com.example.core.dto.SubscriptionResponse;
import com.example.core.dto.UpdateSubscriptionAddressRequest;
import com.example.core.dto.UpdateSubscriptionSlotRequest;
import com.example.core.dto.mapper.DtoMapper;
import com.example.core.model.NotificationType;
import com.example.core.model.User;
import com.example.core.service.AuditService;
import com.example.core.service.NotificationService;
import com.example.core.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Контроллер подписок: создание, получение списка, отмена, пауза, возобновление.
 */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final DtoMapper dtoMapper;
    private final AuditService auditService;
    private final NotificationService notificationService;

    /** Внедрение сервиса подписок и маппера DTO. */
    public SubscriptionController(
            SubscriptionService subscriptionService,
            DtoMapper dtoMapper,
            AuditService auditService,
            NotificationService notificationService
    ) {
        this.subscriptionService = subscriptionService;
        this.dtoMapper = dtoMapper;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    /** Оформить новую подписку текущему пользователю. */
    @PostMapping
    public ResponseEntity<?> createSubscription(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateSubscriptionRequest request) {

        try {
            var subscription = subscriptionService.createSubscription(
                    currentUser,
                    request.getPlan(),
                    request.getAddress(),
                    request.getPickupSlot(),
                    request.getStartDate(),
                    request.getLat(),
                    request.getLng(),
                    request.getCadenceDays()
            );
            notificationService.enqueueInApp(
                    currentUser,
                    NotificationType.SUBSCRIPTION_UPDATE,
                    "Подписка оформлена",
                    "Подписка №" + subscription.getId() + " успешно создана",
                    null,
                    subscription.getId(),
                    "subscription-created-" + subscription.getId()
            );
            var response = dtoMapper.toSubscriptionResponse(subscription);
            return ResponseEntity.status(201).body(response);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось оформить подписку");
        }
    }

    /** Получить все подписки текущего пользователя. */
    @GetMapping
    public ResponseEntity<List<SubscriptionResponse>> getSubscriptions(
            @AuthenticationPrincipal User currentUser) {

        var subscriptions = subscriptionService.getSubscriptionsForUser(currentUser);
        var responses = subscriptions.stream()
                .map(dtoMapper::toSubscriptionResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /** Получить подписки для управления (активные и приостановленные). */
    @GetMapping("/active")
    public ResponseEntity<List<SubscriptionResponse>> getActiveSubscriptions(
            @AuthenticationPrincipal User currentUser) {

        var subscriptions = subscriptionService.getActiveSubscriptionsForUser(currentUser);
        var responses = subscriptions.stream()
                .map(dtoMapper::toSubscriptionResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /** Отменить подписку немедленно (статус CANCELED). */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelSubscription(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {

        try {
            subscriptionService.cancelSubscription(id, currentUser);
            notificationService.enqueueInApp(
                    currentUser,
                    NotificationType.SUBSCRIPTION_UPDATE,
                    "Подписка отменена",
                    "Подписка №" + id + " отменена",
                    null,
                    id,
                    "subscription-cancel-" + id
            );
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** Приостановить активную подписку (статус PAUSED). */
    @PatchMapping("/{id}/pause")
    public ResponseEntity<?> pauseSubscription(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {

        try {
            var subscription = subscriptionService.pauseSubscription(id, currentUser);
            notificationService.enqueueInApp(
                    currentUser,
                    NotificationType.SUBSCRIPTION_UPDATE,
                    "Подписка приостановлена",
                    "Подписка №" + id + " поставлена на паузу",
                    null,
                    id,
                    "subscription-pause-" + id
            );
            var response = dtoMapper.toSubscriptionResponse(subscription);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** Возобновить приостановленную подписку (статус ACTIVE). */
    @PatchMapping("/{id}/resume")
    public ResponseEntity<?> resumeSubscription(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {

        try {
            var subscription = subscriptionService.resumeSubscription(id, currentUser);
            notificationService.enqueueInApp(
                    currentUser,
                    NotificationType.SUBSCRIPTION_UPDATE,
                    "Подписка возобновлена",
                    "Подписка №" + id + " возобновлена",
                    null,
                    id,
                    "subscription-resume-" + id
            );
            var response = dtoMapper.toSubscriptionResponse(subscription);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** Пропустить ближайший вывоз подписки. */
    @PatchMapping("/{id}/skip-next")
    public ResponseEntity<?> skipNextPickup(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id
    ) {
        try {
            var subscription = subscriptionService.skipNextPickup(id, currentUser);
            notificationService.enqueueInApp(
                    currentUser,
                    NotificationType.SUBSCRIPTION_UPDATE,
                    "Вывоз перенесен",
                    "Ближайший вывоз подписки №" + id + " перенесен",
                    null,
                    id,
                    "subscription-skip-" + id
            );
            var response = dtoMapper.toSubscriptionResponse(subscription);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** Обновить адрес подписки. */
    @PatchMapping("/{id}/address")
    public ResponseEntity<?> updateAddress(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSubscriptionAddressRequest request
    ) {
        try {
            var subscription = subscriptionService.updateAddress(
                    id,
                    currentUser,
                    request.getAddress(),
                    request.getLat(),
                    request.getLng()
            );
            notificationService.enqueueInApp(
                    currentUser,
                    NotificationType.SUBSCRIPTION_UPDATE,
                    "Адрес обновлен",
                    "Адрес подписки №" + id + " успешно обновлен",
                    null,
                    id,
                    "subscription-address-" + id
            );
            var response = dtoMapper.toSubscriptionResponse(subscription);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** Обновить временной слот подписки. */
    @PatchMapping("/{id}/slot")
    public ResponseEntity<?> updateSlot(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSubscriptionSlotRequest request
    ) {
        try {
            var subscription = subscriptionService.updatePickupSlot(id, currentUser, request.getPickupSlot());
            notificationService.enqueueInApp(
                    currentUser,
                    NotificationType.SUBSCRIPTION_UPDATE,
                    "Слот обновлен",
                    "Слот подписки №" + id + " обновлен",
                    null,
                    id,
                    "subscription-slot-" + id
            );
            var response = dtoMapper.toSubscriptionResponse(subscription);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** Продлить активную/приостановленную подписку на срок выбранного плана. */
    @PatchMapping("/{id}/extend")
    public ResponseEntity<?> extendSubscription(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody(required = false) ExtendSubscriptionRequest request
    ) {
        try {
            var subscription = subscriptionService.extendSubscription(
                    id,
                    currentUser,
                    request == null ? null : request.getPlan()
            );
            notificationService.enqueueInApp(
                    currentUser,
                    NotificationType.SUBSCRIPTION_UPDATE,
                    "Подписка продлена",
                    "Подписка №" + id + " успешно продлена",
                    null,
                    id,
                    "subscription-extend-" + id
            );
            var response = dtoMapper.toSubscriptionResponse(subscription);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<?> rescheduleSubscription(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody(required = false) RescheduleSubscriptionRequest request
    ) {
        try {
            var subscription = subscriptionService.rescheduleNextPickup(
                    id,
                    currentUser,
                    request == null ? null : request.getNextPickupDate(),
                    request == null ? null : request.getPickupSlot(),
                    request == null ? null : request.getReason()
            );
            notificationService.enqueueInApp(
                    currentUser,
                    NotificationType.SUBSCRIPTION_UPDATE,
                    "График обновлен",
                    "Ближайший вывоз подписки №" + id + " перенесен",
                    null,
                    id,
                    "subscription-reschedule-" + id
            );
            return ResponseEntity.ok(dtoMapper.toSubscriptionResponse(subscription));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<?> subscriptionTimeline(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        try {
            var subscription = subscriptionService.getSubscriptionForTimeline(id, currentUser);
            List<AuditEventResponse> timeline = auditService.getTimeline(
                            "SUBSCRIPTION_ID",
                            String.valueOf(subscription.getId()),
                            limit
                    ).stream()
                    .map(event -> AuditEventResponse.builder()
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
                            .build())
                    .toList();
            return ResponseEntity.ok(timeline);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        String safeMessage = (message == null || message.isBlank()) ? "Ошибка запроса" : message;
        return ResponseEntity.status(status).body(Map.of("message", safeMessage));
    }
}
