package com.example.rate_limiter.core;

public interface RateLimitRuleResolver {
    int resolveLimit(String userId, String api);
}
