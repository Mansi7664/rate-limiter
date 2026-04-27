package com.example.rate_limiter.core;

public interface RateLimiter {
    boolean allowRequest(String userId, String api);
}
