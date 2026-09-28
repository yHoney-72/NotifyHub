package com.notifyhub.notification.sms;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "notifyhub.sms.provider",
        havingValue = "TWILIO",
        matchIfMissing = false
)
public class TwilioSmsProvider implements SmsProvider {
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    public TwilioSmsProvider(
            @Value("${twilio.account-sid}") String accountSid,
            @Value("${twilio.auth-token}") String authToken,
            @Value("${twilio.phone-number}") String fromNumber) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        Twilio.init(accountSid, authToken);
    }

    @Override
    public String getProviderName() {
        return "TWILIO";
    }

    @Override
    public String send(String recipient, String content) {
        Message message = Message.creator(
                new PhoneNumber(recipient),
                new PhoneNumber(fromNumber),
                content
        ).create();
        return message.getSid();
    }
}