package com.notifyhub.messaging.producer;

import com.notifyhub.messaging.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventProducer {
    private static final String TOPIC="notification-events";
    private final KafkaTemplate<String,NotificationEvent> kafkaTemplate;

    public void publish(NotificationEvent event) {
        publish(TOPIC,event);
    }

    public void publish(String topic,NotificationEvent event) {
        try {
            kafkaTemplate.send(topic, String.valueOf(event.notificationId()), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka publish failed: notificationId={}", event.notificationId(), ex);
                        } else {
                            log.info("Kafka event published: notificationId={}", event.notificationId());
                        }
                    });
        } catch(Exception e) {
            log.error("Kafka publish failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish notification event",e);
        }
    }
}