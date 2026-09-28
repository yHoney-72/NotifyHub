package com.notifyhub.email;

import com.notifyhub.enums.Channel;
import com.notifyhub.notification.email.EmailNotificationSender;
import com.notifyhub.notification.email.EmailProvider;
import com.notifyhub.notification.sender.DeliveryResult;
import com.notifyhub.notification.sender.NotificationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailNotificationSenderTest {

    @Test
    void shouldSendEmailSuccessfully() {
        EmailProvider emailProvider = mock(EmailProvider.class);
        NotificationContext context = mock(NotificationContext.class);

        when(context.getRecipient()).thenReturn("test@gmail.com");
        when(context.getSubject()).thenReturn("Test Subject");
        when(context.getRenderedContent()).thenReturn("Test Content");
        when(emailProvider.send("test@gmail.com", "Test Subject", "Test Content"))
                .thenReturn("email-123");

        EmailNotificationSender sender = new EmailNotificationSender(emailProvider);

        DeliveryResult result = sender.send(context);

        assertTrue(result.isSuccess());
        assertEquals("email-123", result.getProviderMessageId());
        assertNull(result.getErrorMessage());
        verify(emailProvider).send("test@gmail.com", "Test Subject", "Test Content");
    }

    @Test
    void shouldHandleEmailFailure() {
        EmailProvider emailProvider = mock(EmailProvider.class);
        NotificationContext context = mock(NotificationContext.class);

        when(context.getRecipient()).thenReturn("test@gmail.com");
        when(context.getSubject()).thenReturn("Test Subject");
        when(context.getRenderedContent()).thenReturn("Test Content");
        when(emailProvider.send("test@gmail.com", "Test Subject", "Test Content"))
                .thenThrow(new RuntimeException("SMTP failed"));

        EmailNotificationSender sender = new EmailNotificationSender(emailProvider);

        DeliveryResult result = sender.send(context);

        assertFalse(result.isSuccess());
        assertNull(result.getProviderMessageId());
        assertEquals("SMTP failed", result.getErrorMessage());
        verify(emailProvider).send("test@gmail.com", "Test Subject", "Test Content");
    }

    @Test
    void shouldReturnEmailChannel() {
        EmailProvider emailProvider = mock(EmailProvider.class);
        EmailNotificationSender sender = new EmailNotificationSender(emailProvider);

        assertEquals(Channel.EMAIL, sender.getChannel());
    }
}