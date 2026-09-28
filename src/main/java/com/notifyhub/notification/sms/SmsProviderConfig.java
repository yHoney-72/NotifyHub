package com.notifyhub.notification.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
public class SmsProviderConfig {

    @Bean
    public SmsProvider selectedSmsProvider(
            List<SmsProvider> providers,
            @Value("${notifyhub.sms.provider:TWILIO}") String providerName) {

        log.info("Selected SMS provider: {}", providerName);


        return providers.stream()
                .filter(provider ->
                        provider.getProviderName()
                                .equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "SMS provider not found: " + providerName
                        ));
    }
}