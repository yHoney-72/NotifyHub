package com.notifyhub.messaging;

import com.notifyhub.TestcontainersSupport;
import com.notifyhub.enums.Channel;
import com.notifyhub.messaging.event.NotificationEvent;
import com.notifyhub.messaging.producer.NotificationEventProducer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class KafkaIntegrationTest extends TestcontainersSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15.8");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerPostgresProperties(registry, POSTGRES);
    }
    @Autowired
    private NotificationEventProducer notificationEventProducer;

    @Test
    void shouldPublishAndConsumeNotificationEvent() {
        String topic = "notification-events-test";
        String key = "kafka-test-" + UUID.randomUUID();
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-test-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.notifyhub.messaging.event");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, NotificationEvent.class);

        try (Consumer<String, NotificationEvent> consumer =
                     new DefaultKafkaConsumerFactory<String, NotificationEvent>(config).createConsumer()) {
            consumer.subscribe(Collections.singletonList(topic));

            long timeout = System.currentTimeMillis() + 10000;
            while (consumer.assignment().isEmpty() && System.currentTimeMillis() < timeout) {
                consumer.poll(Duration.ofMillis(500));
            }

            assertFalse(consumer.assignment().isEmpty(), "Kafka test consumer did not get partition assignment");

            NotificationEvent event = new NotificationEvent(
                    999999L,
                    Channel.EMAIL,
                    "kafka-test@gmail.com",
                    null,
                    null,
                    "Kafka Test",
                    "Kafka integration test"
            );

            notificationEventProducer.publish(topic, event);

            boolean found = false;
            long endTime = System.currentTimeMillis() + 10000;

            while (System.currentTimeMillis() < endTime) {
                for (ConsumerRecord<String, NotificationEvent> record : consumer.poll(Duration.ofMillis(500))) {
                    if (String.valueOf(event.notificationId()).equals(record.key())) {
                        NotificationEvent received = record.value();
                        assertEquals(event.notificationId(), received.notificationId());
                        assertEquals(event.channel(), received.channel());
                        assertEquals(event.recipient(), received.recipient());
                        assertEquals(event.subject(), received.subject());
                        assertEquals(event.renderedContent(), received.renderedContent());
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }

            assertTrue(found, "NotificationEvent was not received from Kafka");
        }
    }
}