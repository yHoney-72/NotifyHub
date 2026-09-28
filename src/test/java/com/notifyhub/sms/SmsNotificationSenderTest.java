package com.notifyhub.sms;

import com.notifyhub.notification.sender.DeliveryResult;
import com.notifyhub.notification.sender.NotificationContext;
import com.notifyhub.notification.sms.SmsNotificationSender;
import com.notifyhub.notification.sms.SmsProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmsNotificationSenderTest {

    @Test
    void shouldSendCustomSms() {
        SmsProvider smsProvider = mock(SmsProvider.class);
        NotificationContext context = mock(NotificationContext.class);

        when(context.getRecipient()).thenReturn("+919198974465");
        when(context.getRenderedContent()).thenReturn("Hello! This is a custom NotifyHub SMS.");
        when(smsProvider.send("+919198974465", "Hello! This is a custom NotifyHub SMS."))
                .thenReturn("SM-test-123");

        SmsNotificationSender sender = new SmsNotificationSender(smsProvider);

        DeliveryResult result = sender.send(context);

        assertTrue(result.isSuccess());
        assertEquals("SM-test-123", result.getProviderMessageId());
        verify(smsProvider).send(
                "+919198974465",
                "Hello! This is a custom NotifyHub SMS."
        );
    }

    @Test
    void shouldHandleSmsFailure() {
        SmsProvider smsProvider = mock(SmsProvider.class);
        NotificationContext context = mock(NotificationContext.class);

        when(context.getRecipient()).thenReturn("+919198974465");
        when(context.getRenderedContent()).thenReturn("Test SMS");
        when(smsProvider.send("+919198974465", "Test SMS"))
                .thenThrow(new RuntimeException("Twilio failed"));

        SmsNotificationSender sender = new SmsNotificationSender(smsProvider);

        DeliveryResult result = sender.send(context);

        assertFalse(result.isSuccess());
        assertEquals("Twilio failed", result.getErrorMessage());
        verify(smsProvider).send("+919198974465", "Test SMS");
    }
}