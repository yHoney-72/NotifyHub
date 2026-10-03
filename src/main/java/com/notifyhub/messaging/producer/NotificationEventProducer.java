package com.notifyhub.messaging.producer;

import com.notifyhub.messaging.event.NotificationEvent;
import com.notifyhub.monitoring.NotifyHubMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventProducer {

    private static final String TOPIC = "notification-events";

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final NotifyHubMetrics metrics;

    public CompletableFuture<SendResult<String, NotificationEvent>> publish(
            NotificationEvent event) {

        return publish(TOPIC, event);
    }

    public CompletableFuture<SendResult<String, NotificationEvent>> publish(
            String topic,
            NotificationEvent event) {

        return kafkaTemplate
                .send(topic, String.valueOf(event.notificationId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        metrics.kafkaPublishFailed();
                        log.error(
                                "Kafka publish failed: notificationId={}",
                                event.notificationId(),
                                ex
                        );
                    } else {
                        log.info(
                                "Kafka event published: notificationId={}",
                                event.notificationId()
                        );
                    }
                });
    }
}