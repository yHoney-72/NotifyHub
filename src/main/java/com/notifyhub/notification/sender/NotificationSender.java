package com.notifyhub.notification.sender;

import com.notifyhub.enums.Channel;

public interface NotificationSender {
    Channel getChannel();

    DeliveryResult send(NotificationContext context);
}
