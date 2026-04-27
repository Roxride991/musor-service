package com.example.core.repository;

import com.example.core.model.NotificationStatus;
import com.example.core.model.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    List<UserNotification> findTop200ByUserIdOrderByCreatedAtDesc(Long userId);

    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<UserNotification> findTop500ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            NotificationStatus status,
            OffsetDateTime scheduledAt
    );

    boolean existsByDedupeKey(String dedupeKey);
}
