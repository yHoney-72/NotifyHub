package com.notifyhub.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RefreshRateLimiterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RefreshRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new RefreshRateLimiter(redisTemplate);
    }

    @Test
    void shouldAllowFirstRequestAndSetExpiration() {
        when(valueOperations.increment("notifyhub:rate-limit:refresh:127.0.0.1"))
                .thenReturn(1L);

        assertTrue(rateLimiter.allow("127.0.0.1"));

        verify(redisTemplate).expire(
                "notifyhub:rate-limit:refresh:127.0.0.1",
                Duration.ofSeconds(60));
    }

    @Test
    void shouldAllowRequestsOneThroughFive() {
        when(valueOperations.increment("notifyhub:rate-limit:refresh:client"))
                .thenReturn(1L, 2L, 3L, 4L, 5L);

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.allow("client"));
        }

        verify(redisTemplate, times(1))
                .expire("notifyhub:rate-limit:refresh:client", Duration.ofSeconds(60));
    }

    @Test
    void shouldRejectSixthRequest() {
        when(valueOperations.increment("notifyhub:rate-limit:refresh:client"))
                .thenReturn(1L, 2L, 3L, 4L, 5L, 6L);

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.allow("client"));
        }

        assertFalse(rateLimiter.allow("client"));
    }
}
