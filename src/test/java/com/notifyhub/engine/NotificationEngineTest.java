package com.notifyhub.engine;

import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.messaging.event.NotificationEvent;
import com.notifyhub.monitoring.NotifyHubMetrics;
import com.notifyhub.notification.entity.Notification;
import com.notifyhub.notification.repository.NotificationRepository;
import com.notifyhub.notification.sender.DeliveryResult;
import com.notifyhub.notification.sender.NotificationContext;
import com.notifyhub.notification.sender.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEngineTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationSender notificationSender;

    @Mock
    private NotifyHubMetrics metrics;

    @InjectMocks
    private NotificationEngine notificationEngine;

    private NotificationEvent event;
    private Notification notification;

    @BeforeEach
    void setUp() {

        notification = new Notification();

        notification.setIdempotencyKey("engine-test-123");
        notification.setSourceApplication("engine-tests");
        notification.setChannel(Channel.EMAIL);
        notification.setRecipient("test@gmail.com");
        notification.setRenderedContent("Hello");
        notification.setStatus(NotificationStatus.PENDING);
        notification.setAttempts(0);

        event = new NotificationEvent(
                1L,
                Channel.EMAIL,
                "test@gmail.com",
                null,
                Map.of(),
                "Test",
                "Hello"
        );
    }

    @Test
    void shouldMarkNotificationAsSentWhenDeliverySucceeds() {

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));

        when(notificationSender.getChannel())
                .thenReturn(Channel.EMAIL);

        when(notificationSender.send(any(NotificationContext.class)))
                .thenReturn(
                        new DeliveryResult(
                                true,
                                "provider-123",
                                null
                        )
                );

        NotificationEngine engine =
                new NotificationEngine(
                        notificationRepository,
                        List.of(notificationSender),
                        metrics
                );

        engine.process(event);

        assertEquals(
                NotificationStatus.SENT,
                notification.getStatus()
        );

        verify(notificationRepository)
                .save(notification);
    }

    @Test
    void shouldMarkRetryingAndThrowExceptionWhenDeliveryFails() {

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));

        when(notificationSender.getChannel())
                .thenReturn(Channel.EMAIL);

        when(notificationSender.send(any(NotificationContext.class)))
                .thenReturn(
                        new DeliveryResult(
                                false,
                                null,
                                "TEST_FAILURE"
                        )
                );

        NotificationEngine engine =
                new NotificationEngine(
                        notificationRepository,
                        List.of(notificationSender),
                        metrics
                );

        assertThrows(
                IllegalStateException.class,
                () -> engine.process(event)
        );

        assertEquals(
                NotificationStatus.RETRYING,
                notification.getStatus()
        );

        assertEquals(
                1,
                notification.getAttempts()
        );

        assertEquals(
                "TEST_FAILURE",
                notification.getFailureReason()
        );

        verify(notificationRepository)
                .save(notification);
    }

    @Test
    void shouldThrowExceptionWhenNotificationDoesNotExist() {

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.empty());

        NotificationEngine engine =
                new NotificationEngine(
                        notificationRepository,
                        List.of(notificationSender),
                        metrics
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.process(event)
        );

        verify(notificationSender, never())
                .send(any());
    }

    @Test
    void shouldThrowExceptionWhenChannelIsUnsupported() {

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));

        when(notificationSender.getChannel())
                .thenReturn(Channel.SMS);

        NotificationEngine engine =
                new NotificationEngine(
                        notificationRepository,
                        List.of(notificationSender),
                        metrics
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.process(event)
        );

        verify(notificationSender, never())
                .send(any());
    }
}