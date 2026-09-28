package com.notifyhub.exception;

public class DuplicateNotificationException extends RuntimeException {
    public DuplicateNotificationException(String message) {
        super(message);
    }

    public DuplicateNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
