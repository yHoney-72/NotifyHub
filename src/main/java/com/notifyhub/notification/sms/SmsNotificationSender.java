package com.notifyhub.notification.sms;

import com.notifyhub.enums.Channel;
import com.notifyhub.notification.sender.DeliveryResult;
import com.notifyhub.notification.sender.NotificationContext;
import com.notifyhub.notification.sender.NotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SmsNotificationSender implements NotificationSender {

    private static String maskRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return "<empty>";
        }
        String digits = recipient.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "***" + digits.substring(digits.length() - 4);
    }

    private final SmsProvider smsProvider;
    public SmsNotificationSender(@Qualifier("selectedSmsProvider")SmsProvider smsProvider) {
        this.smsProvider = smsProvider;
    }

    @Override
    public Channel getChannel() {
        return Channel.SMS;
    }

    @Override
    public DeliveryResult send(NotificationContext context) {

        try {

            String providerMessageId =
                    smsProvider.send(
                            context.getRecipient(),
                            context.getRenderedContent()
                    );

            return new DeliveryResult(
                    true,
                    providerMessageId,
                    null
            );

        } catch (Exception ex) {

            log.error(
                    "SMS sending failed for recipient {}",
                    maskRecipient(context.getRecipient()),
                    ex
            );

            return new DeliveryResult(
                    false,
                    null,
                    ex.getMessage()
            );
        }
    }
}