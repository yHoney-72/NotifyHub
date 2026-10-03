package com.notifyhub.notification.idempotency;

public interface IdempotencyService {
    boolean tryAcquire(Long userId, String sourceApplication, String idempotencyKey);
}