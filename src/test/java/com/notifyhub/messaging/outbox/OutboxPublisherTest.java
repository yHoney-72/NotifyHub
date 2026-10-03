package com.notifyhub.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notifyhub.enums.Channel;
import com.notifyhub.enums.OutboxStatus;
import com.notifyhub.messaging.event.NotificationEvent;
import com.notifyhub.messaging.producer.NotificationEventProducer;
import com.notifyhub.monitoring.NotifyHubMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private NotificationEventProducer notificationEventProducer;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NotifyHubMetrics metrics;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    void shouldMarkEventAsPublishedWhenKafkaSucceeds() throws Exception {
        OutboxEvent event = createEvent(1L, 0);
        NotificationEvent notificationEvent = createNotificationEvent();

        when(outboxEventRepository.claimEvent(
                eq(1L),
                any(LocalDateTime.class)
        )).thenReturn(1);

        when(objectMapper.readValue(
                anyString(),
                eq(NotificationEvent.class)
        )).thenReturn(notificationEvent);

        CompletableFuture<SendResult<String, NotificationEvent>> future =
                CompletableFuture.completedFuture(null);

        when(notificationEventProducer.publish(notificationEvent))
                .thenReturn(future);

        outboxPublisher.publish(event);

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
    }

    @Test
    void shouldReturnToPendingWhenKafkaFailsBeforeMaxAttempts() throws Exception {
        OutboxEvent event = createEvent(2L, 0);
        NotificationEvent notificationEvent = createNotificationEvent();

        when(outboxEventRepository.claimEvent(
                eq(2L),
                any(LocalDateTime.class)
        )).thenReturn(1);

        when(objectMapper.readValue(
                anyString(),
                eq(NotificationEvent.class)
        )).thenReturn(notificationEvent);

        CompletableFuture<SendResult<String, NotificationEvent>> future =
                new CompletableFuture<>();

        future.completeExceptionally(
                new RuntimeException("Kafka unavailable")
        );

        when(notificationEventProducer.publish(notificationEvent))
                .thenReturn(future);

        outboxPublisher.publish(event);

        assertEquals(OutboxStatus.PENDING, event.getStatus());
    }

    @Test
    void shouldMarkEventAsFailedAfterMaxAttempts() throws Exception {
        OutboxEvent event = createEvent(3L, 2);
        NotificationEvent notificationEvent = createNotificationEvent();

        when(outboxEventRepository.claimEvent(
                eq(3L),
                any(LocalDateTime.class)
        )).thenReturn(1);

        when(objectMapper.readValue(
                anyString(),
                eq(NotificationEvent.class)
        )).thenReturn(notificationEvent);

        CompletableFuture<SendResult<String, NotificationEvent>> future =
                new CompletableFuture<>();

        future.completeExceptionally(
                new RuntimeException("Kafka unavailable")
        );

        when(notificationEventProducer.publish(notificationEvent))
                .thenReturn(future);

        outboxPublisher.publish(event);

        assertEquals(OutboxStatus.FAILED, event.getStatus());
    }

    @Test
    void shouldNotPublishWhenEventCannotBeClaimed() {
        OutboxEvent event = createEvent(4L, 0);

        when(outboxEventRepository.claimEvent(
                eq(4L),
                any(LocalDateTime.class)
        )).thenReturn(0);

        outboxPublisher.publish(event);

        verify(notificationEventProducer, never())
                .publish(any(NotificationEvent.class));
    }

    @Test
    void shouldRecoverStaleProcessingEvent() {
        OutboxEvent event = createEvent(5L, 1);

        event.setStatus(OutboxStatus.PROCESSING);
        event.setProcessingStartedAt(
                LocalDateTime.now().minusMinutes(10)
        );

        when(outboxEventRepository
                .findByStatusAndProcessingStartedAtBefore(
                        eq(OutboxStatus.PROCESSING),
                        any(LocalDateTime.class)
                ))
                .thenReturn(List.of(event));

        outboxPublisher.recoverStaleEvents();

        assertEquals(OutboxStatus.PENDING, event.getStatus());
    }

    @Test
    void shouldMarkStaleEventAsFailedAfterMaxAttempts() {
        OutboxEvent event = createEvent(6L, 3);

        event.setStatus(OutboxStatus.PROCESSING);
        event.setProcessingStartedAt(
                LocalDateTime.now().minusMinutes(10)
        );

        when(outboxEventRepository
                .findByStatusAndProcessingStartedAtBefore(
                        eq(OutboxStatus.PROCESSING),
                        any(LocalDateTime.class)
                ))
                .thenReturn(List.of(event));

        outboxPublisher.recoverStaleEvents();

        assertEquals(OutboxStatus.FAILED, event.getStatus());
    }

    private OutboxEvent createEvent(Long id, int attempts) {
        OutboxEvent event = new OutboxEvent();

        event.setId(id);
        event.setAggregateType("Notification");
        event.setAggregateId(100L);
        event.setEventType("NotificationCreated");
        event.setPayload("{}");
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(attempts);
        event.setCreatedAt(LocalDateTime.now());

        return event;
    }

    private NotificationEvent createNotificationEvent() {
        return new NotificationEvent(
                100L,
                Channel.EMAIL,
                "test@example.com",
                null,
                Map.of(),
                "Test Subject",
                "Test Body"
        );
    }
}