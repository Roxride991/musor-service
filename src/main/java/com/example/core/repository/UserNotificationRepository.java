package com.example.core.repository;

import com.example.core.model.NotificationStatus;
import com.example.core.model.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    List<UserNotification> findTop200ByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserNotification> findTop500ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            NotificationStatus status,
            OffsetDateTime scheduledAt
    );

    boolean existsByDedupeKey(String dedupeKey);
}
