package com.notifyhub.notification.email;

import com.notifyhub.enums.Channel;
import com.notifyhub.notification.sender.DeliveryResult;
import com.notifyhub.notification.sender.NotificationContext;
import com.notifyhub.notification.sender.NotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EmailNotificationSender implements NotificationSender {

    private static String maskRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return "<empty>";
        }
        int at = recipient.indexOf('@');
        if (at > 0) {
            String local = recipient.substring(0, Math.min(2, at));
            String domain = recipient.substring(at + 1);
            int dot = domain.indexOf('.');
            String domainName = dot >= 0 ? domain.substring(0, dot) : domain;
            String domainSuffix = dot >= 0 ? domain.substring(dot) : "";
            String maskedDomain = domainName.length() <= 2 ? "**" : domainName.substring(0, 2) + "***";
            return local + "***@" + maskedDomain + domainSuffix;
        }
        if (recipient.length() <= 4) {
            return "****";
        }
        return recipient.substring(0, 2) + "******" + recipient.substring(recipient.length() - 2);
    }

    private final EmailProvider emailProvider;

    public EmailNotificationSender(
            @Qualifier("selectedEmailProvider")
            EmailProvider emailProvider
    ) {
        this.emailProvider = emailProvider;
    }

    @Override
    public Channel getChannel() {
        return Channel.EMAIL;
    }

    @Override
    public DeliveryResult send(NotificationContext context) {

        try {

            String providerMessageId = emailProvider.send(
                    context.getRecipient(),
                    context.getSubject(),
                    context.getRenderedContent()
            );

            return new DeliveryResult(
                    true,
                    providerMessageId,
                    null
            );

        } catch (Exception ex) {

            log.error(
                    "Email sending failed for recipient {}",
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