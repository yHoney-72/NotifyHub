package com.notifyhub.notification.sms;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GenericSmsProvider implements SmsProvider {

    @Override
    public String getProviderName() {
        return "GENERIC";
    }

    @Override
    public String send(
            String recipient,
            String content) {

        // Testing provider
        return UUID.randomUUID().toString();
    }
}