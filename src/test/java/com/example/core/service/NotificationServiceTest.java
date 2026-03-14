package com.example.core.service;

import com.example.core.model.NotificationChannel;
import com.example.core.model.NotificationStatus;
import com.example.core.model.NotificationType;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.UserNotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    @Test
    void enqueueShouldSkipWhenDedupeExists() {
        UserNotificationRepository notificationRepository = mock(UserNotificationRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        NotificationService service = new NotificationService(notificationRepository, orderRepository);

        User user = User.builder()
                .id(1L)
                .name("User")
                .phone("+79990000002")
                .password("x")
                .userRole(UserRole.CLIENT)
                .build();

        when(notificationRepository.existsByDedupeKey("dedupe-1")).thenReturn(true);

        service.enqueue(
                user,
                NotificationType.SYSTEM,
                NotificationChannel.IN_APP,
                "Title",
                "Message",
                null,
                null,
                "dedupe-1",
                OffsetDateTime.now()
        );

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void dispatchQueuedNotificationsShouldMarkSent() {
        UserNotificationRepository notificationRepository = mock(UserNotificationRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        NotificationService service = new NotificationService(notificationRepository, orderRepository);

        com.example.core.model.UserNotification queued = com.example.core.model.UserNotification.builder()
                .id(10L)
                .user(User.builder().id(3L).userRole(UserRole.CLIENT).name("U").phone("+79990000003").password("x").build())
                .type(NotificationType.SYSTEM)
                .channel(NotificationChannel.IN_APP)
                .status(NotificationStatus.QUEUED)
                .title("t")
                .message("m")
                .scheduledAt(OffsetDateTime.now().minusMinutes(1))
                .build();

        when(notificationRepository.findTop500ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(any(), any()))
                .thenReturn(List.of(queued));

        service.dispatchQueuedNotifications();

        verify(notificationRepository, times(1)).save(any());
    }
}
