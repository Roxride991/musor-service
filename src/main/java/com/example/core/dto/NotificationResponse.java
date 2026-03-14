package com.example.core.dto;

import com.example.core.model.NotificationChannel;
import com.example.core.model.NotificationStatus;
import com.example.core.model.NotificationType;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class NotificationResponse {
    Long id;
    NotificationType type;
    NotificationChannel channel;
    NotificationStatus status;
    String title;
    String message;
    Long orderId;
    Long subscriptionId;
    OffsetDateTime scheduledAt;
    OffsetDateTime sentAt;
    OffsetDateTime readAt;
    OffsetDateTime createdAt;
}
