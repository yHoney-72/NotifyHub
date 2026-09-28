package com.notifyhub.notification.email;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "notifyhub.email.provider",
        havingValue = "SMTP",
        matchIfMissing = false
)
public class SmtpEmailProvider implements EmailProvider {
    private final JavaMailSender mailSender;

    @Override
    public String getProviderName() {
        return "SMTP";
    }

    @Override
    public String send(String recipient, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
        return UUID.randomUUID().toString();
    }
}