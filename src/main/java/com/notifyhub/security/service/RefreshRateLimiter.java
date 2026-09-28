package com.notifyhub.security.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RefreshRateLimiter {

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_SECONDS = 60;

    private final Map<String, RateLimitEntry> requests =
            new ConcurrentHashMap<>();

    public boolean allow(String key) {

        long now = Instant.now().getEpochSecond();

        RateLimitEntry entry =
                requests.compute(
                        key,
                        (k, existing) -> {

                            if (existing == null ||
                                    now - existing.windowStart
                                            >= WINDOW_SECONDS) {

                                return new RateLimitEntry(
                                        now,
                                        1
                                );
                            }

                            if (existing.count >= MAX_REQUESTS) {
                                existing.count++;
                                return existing;
                            }

                            existing.count++;
                            return existing;
                        }
                );

        return entry.count <= MAX_REQUESTS;
    }

    private static class RateLimitEntry {

        private final long windowStart;
        private int count;

        private RateLimitEntry(
                long windowStart,
                int count) {

            this.windowStart = windowStart;
            this.count = count;
        }
    }
}