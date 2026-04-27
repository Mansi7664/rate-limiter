package com.example.rate_limiter.core;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryRateLimitStore implements RateLimitStore {

    private static final class Window {
        long count;
        long expiresAtMillis;
    }

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long cleanupInterval;
    private final AtomicLong operationCount = new AtomicLong();
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean();

    public InMemoryRateLimitStore() {
        this(Clock.systemUTC(), 64);
    }

    public InMemoryRateLimitStore(Clock clock) {
        this(clock, 64);
    }

    public InMemoryRateLimitStore(Clock clock, long cleanupInterval) {
        this.clock = clock;
        if (cleanupInterval <= 0) {
            throw new IllegalArgumentException("cleanupInterval must be greater than 0");
        }
        this.cleanupInterval = cleanupInterval;
    }

    @Override
    public long increment(String key, long ttlSeconds) {
        long now = clock.millis();
        maybeEvictExpiredWindows(now);
        Window updated = windows.compute(key, (k, existing) -> {
            if (existing == null || now >= existing.expiresAtMillis) {
                Window fresh = new Window();
                fresh.count = 1L;
                fresh.expiresAtMillis = now + ttlSeconds * 1000L;
                return fresh;
            }
            existing.count++;
            return existing;
        });
        return updated.count;
    }

    public int activeWindowCount() {
        return windows.size();
    }

    private void maybeEvictExpiredWindows(long now) {
        long currentOperation = operationCount.incrementAndGet();
        if (currentOperation % cleanupInterval != 0) {
            return;
        }
        if (!cleanupInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            windows.entrySet().removeIf(entry -> now >= entry.getValue().expiresAtMillis);
        } finally {
            cleanupInProgress.set(false);
        }
    }
}
