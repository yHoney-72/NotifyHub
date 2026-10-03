package com.notifyhub.messaging;

import com.notifyhub.engine.NotificationEngine;
import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.messaging.consumer.NotificationEventConsumer;
import com.notifyhub.messaging.event.NotificationEvent;
import com.notifyhub.notification.entity.Notification;
import com.notifyhub.notification.repository.NotificationRepository;
import com.notifyhub.monitoring.NotifyHubMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {
    @Mock private NotificationEngine notificationEngine;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotifyHubMetrics metrics;

    @InjectMocks
    private NotificationEventConsumer consumer;

    @Test
    void shouldSkipDuplicateSentNotification() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setStatus(NotificationStatus.SENT);

        NotificationEvent event = new NotificationEvent(
                1L,
                Channel.EMAIL,
                "test@example.com",
                null,
                Map.of(),
                "Test",
                "Hello"
        );

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));

        consumer.consume(event);

        verify(notificationEngine, never()).process(any());
    }

    @Test
    void shouldProcessNotificationWhenNotSent() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setStatus(NotificationStatus.PENDING);

        NotificationEvent event = new NotificationEvent(
                1L,
                Channel.EMAIL,
                "test@example.com",
                null,
                Map.of(),
                "Test",
                "Hello"
        );

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));

        consumer.consume(event);

        verify(notificationEngine).process(event);
    }
}