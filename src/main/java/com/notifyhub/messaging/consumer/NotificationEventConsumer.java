package com.notifyhub.messaging.consumer;

import com.notifyhub.engine.NotificationEngine;
import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.messaging.event.NotificationEvent;
import com.notifyhub.notification.entity.Notification;
import com.notifyhub.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {
    private final NotificationEngine notificationEngine;
    private final NotificationRepository notificationRepository;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(
                    delay = 1000,
                    multiplier = 2.0
            )
    )
    @KafkaListener(
            topics = "notification-events",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(NotificationEvent event) {
        log.info(
                "Processing notification event: id={}, channel={}",
                event.notificationId(),
                event.channel()
        );

        notificationEngine.process(event);
    }

    @DltHandler
    public void handleDlt(NotificationEvent event) {
        log.error(
                "Notification permanently failed: id={}, channel={}, attempts=3",
                event.notificationId(),
                event.channel()
        );

        Notification notification = notificationRepository
                .findById(event.notificationId())
                .orElse(null);

        if (notification == null) {
            log.error(
                    "Notification not found while processing DLT: id={}",
                    event.notificationId()
            );
            return;
        }

        notification.setStatus(NotificationStatus.FAILED);
        notification.setFailureReason(
                "Notification delivery exhausted retries after DLT for notification " + event.notificationId()
        );
        notificationRepository.save(notification);
    }
}