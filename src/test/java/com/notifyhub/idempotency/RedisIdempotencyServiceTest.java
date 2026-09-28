package com.notifyhub.idempotency;

import com.notifyhub.notification.idempotency.RedisIdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisIdempotencyServiceTest {
    @Test
    void shouldAcquireIdempotencyKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        RedisIdempotencyService service = new RedisIdempotencyService(redisTemplate);

        assertTrue(service.tryAcquire("test-key"));
        verify(valueOperations).setIfAbsent(anyString(), eq("1"), any(Duration.class));
    }

    @Test
    void shouldRejectDuplicateIdempotencyKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        RedisIdempotencyService service = new RedisIdempotencyService(redisTemplate);

        assertFalse(service.tryAcquire("duplicate-key"));
        verify(valueOperations).setIfAbsent(anyString(), eq("1"), any(Duration.class));
    }
}