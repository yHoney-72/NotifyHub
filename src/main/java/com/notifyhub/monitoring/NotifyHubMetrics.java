package com.notifyhub.monitoring;

import com.notifyhub.enums.Channel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotifyHubMetrics {

    private static final String CHANNEL_TAG = "channel";
    private static final String CREATED = "notifyhub.notifications.created";
    private static final String SENT = "notifyhub.notifications.sent";
    private static final String FAILED = "notifyhub.notifications.failed";
    private static final String PROCESSING_DURATION =
            "notifyhub.notifications.processing.duration";
    private static final String OUTBOX_PUBLISHED = "notifyhub.outbox.published";
    private static final String OUTBOX_FAILED = "notifyhub.outbox.failed";
    private static final String OUTBOX_RECOVERED = "notifyhub.outbox.recovered";
    private static final String KAFKA_PUBLISH_FAILURES =
            "notifyhub.kafka.publish.failures";

    private final MeterRegistry meterRegistry;

    public void notificationCreated(Channel channel) {
        notificationCounter(CREATED, channel).increment();
    }

    public void notificationSent(Channel channel) {
        notificationCounter(SENT, channel).increment();
    }

    public void notificationFailed(Channel channel) {
        notificationCounter(FAILED, channel).increment();
    }

    public Timer.Sample startNotificationProcessing() {
        return Timer.start(meterRegistry);
    }

    public void recordNotificationProcessing(Timer.Sample sample, Channel channel) {
        sample.stop(notificationTimer(channel));
    }

    public void outboxPublished() {
        meterRegistry.counter(OUTBOX_PUBLISHED).increment();
    }

    public void outboxFailed() {
        meterRegistry.counter(OUTBOX_FAILED).increment();
    }

    public void outboxRecovered() {
        meterRegistry.counter(OUTBOX_RECOVERED).increment();
    }

    public void kafkaPublishFailed() {
        meterRegistry.counter(KAFKA_PUBLISH_FAILURES).increment();
    }

    private Counter notificationCounter(String name, Channel channel) {
        return meterRegistry.counter(name, CHANNEL_TAG, channel.name());
    }

    private Timer notificationTimer(Channel channel) {
        return meterRegistry.timer(PROCESSING_DURATION, CHANNEL_TAG, channel.name());
    }
}
