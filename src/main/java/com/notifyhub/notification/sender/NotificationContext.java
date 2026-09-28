package com.notifyhub.notification.sender;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationContext {

    private String recipient;

    private String subject;

    private String renderedContent;
}