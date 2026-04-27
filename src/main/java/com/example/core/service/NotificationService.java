package com.example.core.service;

import com.example.core.dto.NotificationResponse;
import com.example.core.dto.PageResponse;
import com.example.core.exception.ForbiddenOperationException;
import com.example.core.exception.ResourceNotFoundException;
import com.example.core.model.NotificationChannel;
import com.example.core.model.NotificationStatus;
import com.example.core.model.NotificationType;
import com.example.core.model.Order;
import com.example.core.model.OrderStatus;
import com.example.core.model.User;
import com.example.core.model.UserNotification;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final List<OrderStatus> OPEN_ORDER_STATUSES = List.of(
            OrderStatus.PUBLISHED,
            OrderStatus.ACCEPTED,
            OrderStatus.ON_THE_WAY,
            OrderStatus.PICKED_UP
    );

    private final UserNotificationRepository notificationRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public void enqueueInApp(
            User user,
            NotificationType type,
            String title,
            String message,
            Long orderId,
            Long subscriptionId,
            String dedupeKey
    ) {
        enqueue(user, type, NotificationChannel.IN_APP, title, message, orderId, subscriptionId, dedupeKey, OffsetDateTime.now());
    }

    @Transactional
    public void enqueue(
            User user,
            NotificationType type,
            NotificationChannel channel,
            String title,
            String message,
            Long orderId,
            Long subscriptionId,
            String dedupeKey,
            OffsetDateTime scheduledAt
    ) {
        if (user == null || user.getId() == null) {
            return;
        }

        if (dedupeKey != null && !dedupeKey.isBlank() && notificationRepository.existsByDedupeKey(dedupeKey)) {
            return;
        }

        UserNotification notification = UserNotification.builder()
                .user(user)
                .type(type == null ? NotificationType.SYSTEM : type)
                .channel(channel == null ? NotificationChannel.IN_APP : channel)
                .status(NotificationStatus.QUEUED)
                .title(trimOrFallback(title, "Уведомление"))
                .message(trimOrFallback(message, "Есть обновление"))
                .orderId(orderId)
                .subscriptionId(subscriptionId)
                .dedupeKey(normalizeOptional(dedupeKey))
                .scheduledAt(scheduledAt == null ? OffsetDateTime.now() : scheduledAt)
                .build();

        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(User user, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return notificationRepository.findTop200ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .limit(safeLimit)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listForUser(User user, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 100));
        var notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                user.getId(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.<NotificationResponse>builder()
                .content(notifications.getContent().stream().map(this::toResponse).toList())
                .page(notifications.getNumber())
                .size(notifications.getSize())
                .totalElements(notifications.getTotalElements())
                .totalPages(notifications.getTotalPages())
                .first(notifications.isFirst())
                .last(notifications.isLast())
                .build();
    }

    @Transactional
    public NotificationResponse markAsRead(User user, Long notificationId) {
        UserNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Уведомление не найдено"));

        if (notification.getUser() == null
                || notification.getUser().getId() == null
                || !notification.getUser().getId().equals(user.getId())) {
            throw new ForbiddenOperationException("Уведомление недоступно");
        }

        notification.setStatus(NotificationStatus.READ);
        notification.setReadAt(OffsetDateTime.now());
        if (notification.getSentAt() == null) {
            notification.setSentAt(OffsetDateTime.now());
        }
        return toResponse(notificationRepository.save(notification));
    }

    @Scheduled(fixedDelayString = "${notifications.dispatch.period-ms:30000}")
    @Transactional
    public void dispatchQueuedNotifications() {
        OffsetDateTime now = OffsetDateTime.now();
        List<UserNotification> queue = notificationRepository
                .findTop500ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(NotificationStatus.QUEUED, now);

        for (UserNotification notification : queue) {
            try {
                if (notification.getChannel() == NotificationChannel.SMS) {
                    notification.setStatus(NotificationStatus.FAILED);
                    notification.setErrorMessage("SMS channel is not configured for generic notifications yet");
                } else {
                    notification.setStatus(NotificationStatus.SENT);
                }
                notification.setSentAt(now);
                notificationRepository.save(notification);
            } catch (Exception e) {
                notification.setStatus(NotificationStatus.FAILED);
                notification.setErrorMessage("Dispatch failed: " + e.getMessage());
                notificationRepository.save(notification);
                log.warn("Failed to dispatch notification id={}", notification.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${notifications.reminders.period-ms:900000}")
    @Transactional
    public void schedulePickupReminders() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime reminderWindowStart = now.plusHours(20);
        OffsetDateTime reminderWindowEnd = now.plusHours(28);

        List<Order> tomorrowOrders = orderRepository.findByStatusInAndPickupTimeBetween(
                OPEN_ORDER_STATUSES,
                reminderWindowStart,
                reminderWindowEnd
        );

        for (Order order : tomorrowOrders) {
            if (order.getClient() == null || order.getClient().getId() == null) {
                continue;
            }
            String dedupeKey = "reminder-order-" + order.getId() + "-" + order.getPickupTime().toLocalDate();
            enqueueInApp(
                    order.getClient(),
                    NotificationType.ORDER_REMINDER,
                    "Напоминание о вывозе",
                    "Завтра запланирован вывоз по заказу №" + order.getId() + " в интервал " + order.getPickupTime().toLocalTime(),
                    order.getId(),
                    null,
                    dedupeKey
            );
        }
    }

    public NotificationResponse toResponse(UserNotification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .channel(notification.getChannel())
                .status(notification.getStatus())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .orderId(notification.getOrderId())
                .subscriptionId(notification.getSubscriptionId())
                .scheduledAt(notification.getScheduledAt())
                .sentAt(notification.getSentAt())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private String trimOrFallback(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }
}
