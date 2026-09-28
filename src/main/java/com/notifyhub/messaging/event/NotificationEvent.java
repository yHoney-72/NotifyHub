package com.notifyhub.messaging.event;

import com.notifyhub.enums.Channel;

import java.util.Map;

public record NotificationEvent(
        Long notificationId,
        Channel channel,
        String recipient,
        String templateCode,
        Map<String, Object> data,
        String subject,
        String renderedContent
) {
}