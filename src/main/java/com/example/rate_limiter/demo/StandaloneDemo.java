package com.example.rate_limiter.demo;

import com.example.rate_limiter.config.RateLimitConfig;
import com.example.rate_limiter.core.*;

import java.util.List;

public class StandaloneDemo {
    public static void main(String[] args) {
        RateLimitConfig config = new RateLimitConfig();
        config.setDefaultLimit(100);
        config.setWindowSeconds(3600);
        config.setRules(List.of(
                RateLimitRule.forApi("/api/v1/developers", 80),
                RateLimitRule.forApi("/api/v1/organizations", 300),
                RateLimitRule.forUserApi("user1", "/api/v1/developers", 100),
                RateLimitRule.forUserApi("user2", "/api/v1/developers", 50),
                RateLimitRule.forUserApi("user1", "/api/v1/organizations", 250),
                RateLimitRule.forUserApi("user2", "/api/v1/organizations", 500)
        ));

        RateLimiter limiter = new RateLimitService(
                new InMemoryRateLimitStore(),
                new ConfiguredRateLimitRuleResolver(config),
                config.getWindowSeconds()
        );

        System.out.println("Rate limiter demo");
        System.out.println("user1 /developers override = " + limiter.allowRequest("user1", "/api/v1/developers"));
        System.out.println("user2 /developers override = " + limiter.allowRequest("user2", "/api/v1/developers"));
        System.out.println("user3 /developers api-level = " + limiter.allowRequest("user3", "/api/v1/developers"));
        System.out.println("user1 /organizations override = " + limiter.allowRequest("user1", "/api/v1/organizations"));
        System.out.println("user2 /organizations override = " + limiter.allowRequest("user2", "/api/v1/organizations"));
        System.out.println("user4 /unknown global-default = " + limiter.allowRequest("user4", "/api/v1/unknown"));
    }
}
