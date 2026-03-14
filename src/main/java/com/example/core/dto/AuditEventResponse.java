package com.example.core.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class AuditEventResponse {
    Long id;
    String eventType;
    String outcome;
    Long actorUserId;
    String actorRole;
    String targetType;
    String targetId;
    String clientIp;
    String details;
    OffsetDateTime createdAt;
}
