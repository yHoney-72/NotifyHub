package com.notifyhub.monitoring;

import com.notifyhub.enums.Channel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotifyHubMetricsTest {

    private SimpleMeterRegistry registry;
    private NotifyHubMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new NotifyHubMetrics(registry);
    }

    @Test
    void shouldRecordNotificationCountersWithChannelTags() {
        metrics.notificationCreated(Channel.EMAIL);
        metrics.notificationSent(Channel.EMAIL);
        metrics.notificationFailed(Channel.EMAIL);

        assertEquals(1.0, registry
                .counter("notifyhub.notifications.created", "channel", "EMAIL")
                .count());
        assertEquals(1.0, registry
                .counter("notifyhub.notifications.sent", "channel", "EMAIL")
                .count());
        assertEquals(1.0, registry
                .counter("notifyhub.notifications.failed", "channel", "EMAIL")
                .count());
    }

    @Test
    void shouldRecordNotificationProcessingDurationWithChannelTag() {
        io.micrometer.core.instrument.Timer.Sample sample =
                metrics.startNotificationProcessing();

        metrics.recordNotificationProcessing(sample, Channel.SMS);

        assertEquals(1L, registry
                .timer("notifyhub.notifications.processing.duration",
                        "channel", "SMS")
                .count());
    }

    @Test
    void shouldRecordOutboxAndKafkaCounters() {
        metrics.outboxPublished();
        metrics.outboxFailed();
        metrics.outboxRecovered();
        metrics.kafkaPublishFailed();

        assertEquals(1.0, registry.counter("notifyhub.outbox.published").count());
        assertEquals(1.0, registry.counter("notifyhub.outbox.failed").count());
        assertEquals(1.0, registry.counter("notifyhub.outbox.recovered").count());
        assertEquals(1.0,
                registry.counter("notifyhub.kafka.publish.failures").count());
    }
}
