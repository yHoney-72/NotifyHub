package com.notifyhub.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshRateLimiter {

    private static final String KEY_PREFIX = "notifyhub:rate-limit:refresh:";
    private static final int MAX_REQUESTS = 5;
    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public boolean allow(String key) {
        String redisKey = KEY_PREFIX + key;
        Long requestCount = redisTemplate.opsForValue().increment(redisKey);

        if (requestCount == null) {
            throw new IllegalStateException("Redis did not return a rate-limit counter");
        }

        if (requestCount == 1L) {
            redisTemplate.expire(redisKey, WINDOW);
        }

        return requestCount <= MAX_REQUESTS;
    }
}