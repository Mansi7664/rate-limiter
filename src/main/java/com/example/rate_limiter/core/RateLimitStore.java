package com.example.rate_limiter.core;

public interface RateLimitStore {
    long increment(String key, long ttlSeconds);
}
