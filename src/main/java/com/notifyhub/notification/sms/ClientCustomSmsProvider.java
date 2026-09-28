package com.notifyhub.notification.sms;

import org.springframework.stereotype.Component;

@Component
public class ClientCustomSmsProvider implements SmsProvider {

    @Override
    public String getProviderName() {
        return "CLIENT_CUSTOM";
    }

    @Override
    public String send(
            String recipient,
            String content) {

        // Client-specific SMS provider integration
        return null;
    }
}