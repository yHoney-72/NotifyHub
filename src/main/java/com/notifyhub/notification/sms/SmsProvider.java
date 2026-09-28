package com.notifyhub.notification.sms;

public interface SmsProvider {

    String getProviderName();

    String send(
            String recipient,
            String content
    );
}