package com.notifyhub.notification.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisIdempotencyService implements IdempotencyService {
    private static final String PREFIX = "notifyhub:idempotency:";
    private static final Duration TTL = Duration.ofSeconds(60);
    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryAcquire(String idempotencyKey) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(
                        PREFIX + idempotencyKey,
                        "1",
                        TTL
                )
        );
    }
}