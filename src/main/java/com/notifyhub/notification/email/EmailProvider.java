package com.notifyhub.notification.email;

public interface EmailProvider {
    String getProviderName();
    String send(String recipient,String subject,String content);
}