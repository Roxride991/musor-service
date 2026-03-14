package com.example.core.repository;

import com.example.core.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop200ByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, String targetId);

    long countByEventTypeAndCreatedAtAfter(String eventType, OffsetDateTime createdAt);
}
