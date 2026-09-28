package com.notifyhub.notification.idempotency;

public interface IdempotencyService {
    boolean tryAcquire(String idempotencyKey);
}