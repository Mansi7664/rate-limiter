package com.example.rate_limiter.core;

import java.util.Objects;

public class RateLimitService implements RateLimiter {

    private final RateLimitStore rateLimitStore;
    private final RateLimitRuleResolver ruleResolver;
    private final long windowSeconds;

    public RateLimitService(RateLimitStore rateLimitStore, RateLimitRuleResolver ruleResolver, long windowSeconds) {
        this.rateLimitStore = Objects.requireNonNull(rateLimitStore, "rateLimitStore must not be null");
        this.ruleResolver = Objects.requireNonNull(ruleResolver, "ruleResolver must not be null");
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be greater than 0");
        }
        this.windowSeconds = windowSeconds;
    }

    @Override
    public boolean allowRequest(String userId, String api) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (api == null || api.isBlank()) {
            throw new IllegalArgumentException("api must not be blank");
        }

        int limit = ruleResolver.resolveLimit(userId, api);
        String key = "rate:" + userId + ":" + api;
        long currentCount = rateLimitStore.increment(key, windowSeconds);
        return currentCount <= limit;
    }
}
