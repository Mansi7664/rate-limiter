package com.example.rate_limiter.core;

import com.example.rate_limiter.config.RateLimitConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ConfiguredRateLimitRuleResolver implements RateLimitRuleResolver {
    private final Map<String, Integer> userApiLimits = new HashMap<>();
    private final Map<String, Integer> apiLimits = new HashMap<>();
    private final int defaultLimit;

    public ConfiguredRateLimitRuleResolver(RateLimitConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        config.validate();
        this.defaultLimit = config.getDefaultLimit();
        for (RateLimitRule rule : config.getRules()) {
            if (rule.isApiLevelRule()) {
                apiLimits.put(rule.getApi(), rule.getLimit());
            } else {
                userApiLimits.put(key(rule.getUserId(), rule.getApi()), rule.getLimit());
            }
        }
    }

    @Override
    public int resolveLimit(String userId, String api) {
        Integer userApiLimit = userApiLimits.get(key(userId, api));
        if (userApiLimit != null) {
            return userApiLimit;
        }

        Integer apiLimit = apiLimits.get(api);
        return apiLimit != null ? apiLimit : defaultLimit;
    }

    private String key(String userId, String api) {
        return userId + "::" + api;
    }
}
