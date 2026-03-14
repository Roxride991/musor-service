package com.example.core.service;

import com.example.core.model.AuditEvent;
import com.example.core.model.User;
import com.example.core.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public void log(
            String eventType,
            String outcome,
            User actor,
            String targetType,
            String targetId,
            String details,
            String clientIp
    ) {
        Long actorUserId = actor == null ? null : actor.getId();
        String actorRole = actor == null || actor.getUserRole() == null ? null : actor.getUserRole().name();
        log(eventType, outcome, actorUserId, actorRole, targetType, targetId, details, clientIp);
    }

    public void log(
            String eventType,
            String outcome,
            Long actorUserId,
            String actorRole,
            String targetType,
            String targetId,
            String details,
            String clientIp
    ) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .eventType(safeValue(eventType, 64))
                    .outcome(safeValue(outcome, 16))
                    .actorUserId(actorUserId)
                    .actorRole(safeValue(actorRole, 32))
                    .targetType(safeValue(targetType, 64))
                    .targetId(safeValue(targetId, 128))
                    .details(safeValue(details, 2000))
                    .clientIp(safeValue(clientIp, 64))
                    .build();
            auditEventRepository.save(event);
        } catch (Exception e) {
            // Аудит не должен ломать основную бизнес-операцию.
            log.warn("Failed to persist audit event: type={}, outcome={}, reason={}",
                    eventType, outcome, e.getMessage());
        }
    }

    public List<AuditEvent> getTimeline(String targetType, String targetId, int limit) {
        List<AuditEvent> events = auditEventRepository
                .findTop200ByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        if (events.size() <= safeLimit) {
            return events;
        }
        return events.subList(0, safeLimit);
    }

    private String safeValue(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen);
    }
}
