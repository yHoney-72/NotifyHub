package com.notifyhub.engine;

import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.messaging.event.NotificationEvent;
import com.notifyhub.monitoring.NotifyHubMetrics;
import com.notifyhub.notification.entity.Notification;
import com.notifyhub.notification.repository.NotificationRepository;
import com.notifyhub.notification.sender.DeliveryResult;
import com.notifyhub.notification.sender.NotificationContext;
import com.notifyhub.notification.sender.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Timer;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEngine {
    private final NotificationRepository notificationRepository;
    private final List<NotificationSender> notificationSenders;
    private final NotifyHubMetrics metrics;

    public void process(NotificationEvent event) {
        Timer.Sample sample = metrics.startNotificationProcessing();
        try {
            processNotification(event);
        } finally {
            metrics.recordNotificationProcessing(sample, event.channel());
        }
    }

    private void processNotification(NotificationEvent event) {
        Notification notification = notificationRepository
                .findById(event.notificationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Notification not found: " + event.notificationId()
                ));

        NotificationSender sender = notificationSenders.stream()
                .filter(s -> s.getChannel() == event.channel())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported notification channel: " + event.channel()
                ));

        int attempt = notification.getAttempts() + 1;

        log.info(
                "Notification attempt started: id={}, channel={}, attempt={}",
                event.notificationId(),
                event.channel(),
                attempt
        );

        NotificationContext context = new NotificationContext(
                event.recipient(),
                event.subject(),
                event.renderedContent()
        );

        DeliveryResult result = sender.send(context);

        if (result.isSuccess()) {
            notification.setAttempts(attempt);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notification.setFailureReason(null);
            notificationRepository.save(notification);
            metrics.notificationSent(event.channel());

            log.info(
                    "Notification delivered successfully: id={}, channel={}, attempt={}",
                    event.notificationId(),
                    event.channel(),
                    attempt
            );

            return;
        }

        notification.setAttempts(attempt);
        notification.setStatus(NotificationStatus.RETRYING);
        notification.setFailureReason(result.getErrorMessage());
        notificationRepository.save(notification);

        log.warn(
                "Notification delivery failed: id={}, channel={}, attempt={}, reason={}",
                event.notificationId(),
                event.channel(),
                attempt,
                result.getErrorMessage()
        );

        throw new IllegalStateException(
                "Notification delivery failed: " + result.getErrorMessage()
        );
    }
}