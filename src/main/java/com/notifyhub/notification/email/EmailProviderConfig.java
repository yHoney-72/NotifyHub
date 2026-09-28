package com.notifyhub.notification.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class EmailProviderConfig {

    @Bean
    public EmailProvider selectedEmailProvider(
            List<EmailProvider> providers,
            @Value("${notifyhub.email.provider:SMTP}") String providerName) {

        return providers.stream()
                .filter(provider ->
                        provider.getProviderName()
                                .equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Email provider not found: " + providerName
                        ));
    }
}