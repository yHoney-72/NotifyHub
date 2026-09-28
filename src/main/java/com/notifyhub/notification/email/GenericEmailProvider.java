package com.notifyhub.notification.email;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GenericEmailProvider implements EmailProvider {

    @Override
    public String getProviderName() {
        return "GENERIC";
    }

    @Override
    public String send(
            String recipient,
            String subject,
            String content) {

        // Testing provider.
        // No real email is sent.
        return UUID.randomUUID().toString();
    }
}