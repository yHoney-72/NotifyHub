package com.notifyhub.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notifyhub.enums.OutboxStatus;
import com.notifyhub.messaging.event.NotificationEvent;
import com.notifyhub.messaging.producer.NotificationEventProducer;
import com.notifyhub.monitoring.NotifyHubMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private static final int MAX_ATTEMPTS = 3;

    private final OutboxEventRepository outboxEventRepository;
    private final NotificationEventProducer notificationEventProducer;
    private final ObjectMapper objectMapper;
    private final NotifyHubMetrics metrics;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository
                .findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    public void publish(OutboxEvent outboxEvent) {
        try {
            LocalDateTime processingTime = LocalDateTime.now();

            int claimed = outboxEventRepository.claimEvent(
                    outboxEvent.getId(),
                    processingTime
            );

            if (claimed == 0) {
                return;
            }

            outboxEvent.setStatus(OutboxStatus.PROCESSING);
            outboxEvent.setProcessingStartedAt(processingTime);
            outboxEvent.setAttempts(outboxEvent.getAttempts() + 1);

            NotificationEvent event = objectMapper.readValue(
                    outboxEvent.getPayload(),
                    NotificationEvent.class
            );

            CompletableFuture<SendResult<String, NotificationEvent>> future =
                    notificationEventProducer.publish(event);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    handleFailure(outboxEvent, ex);
                } else {
                    handleSuccess(outboxEvent);
                }
            });

        } catch (Exception ex) {
            handleFailure(outboxEvent, ex);
        }
    }

    private void handleSuccess(OutboxEvent event) {
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(LocalDateTime.now());
        event.setProcessingStartedAt(null);
        event.setLastError(null);

        outboxEventRepository.save(event);

        log.info(
                "Outbox published: outboxId={}, attempts={}",
                event.getId(),
                event.getAttempts()
        );
        metrics.outboxPublished();
    }

    private void handleFailure(OutboxEvent event, Throwable ex) {
        if (event.getAttempts() >= MAX_ATTEMPTS) {
            event.setStatus(OutboxStatus.FAILED);
        } else {
            event.setStatus(OutboxStatus.PENDING);
        }

        event.setProcessingStartedAt(null);
        event.setLastError(ex.getMessage());

        outboxEventRepository.save(event);
        metrics.outboxFailed();

        log.error(
                "Outbox publish failed: outboxId={}, attempts={}",
                event.getId(),
                event.getAttempts(),
                ex
        );
    }

    @Scheduled(fixedDelay = 10000)
    public void recoverStaleEvents() {
        LocalDateTime cutoff =
                LocalDateTime.now().minusMinutes(5);

        List<OutboxEvent> events =
                outboxEventRepository.findByStatusAndProcessingStartedAtBefore(
                        OutboxStatus.PROCESSING,
                        cutoff
                );

        for (OutboxEvent event : events) {
            if (event.getAttempts() >= MAX_ATTEMPTS) {
                event.setStatus(OutboxStatus.FAILED);
                event.setProcessingStartedAt(null);

                outboxEventRepository.save(event);

                log.error(
                        "Stale outbox event exhausted retries: outboxId={}",
                        event.getId()
                );
            } else {
                event.setStatus(OutboxStatus.PENDING);
                event.setProcessingStartedAt(null);

                outboxEventRepository.save(event);
                metrics.outboxRecovered();

                log.warn(
                        "Recovered stale outbox event: outboxId={}, attempts={}",
                        event.getId(),
                        event.getAttempts()
                );
            }
        }
    }
}