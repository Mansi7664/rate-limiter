package com.example.rate_limiter.config;

import com.example.rate_limiter.core.RateLimitRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RateLimitConfig {
    private int defaultLimit = 100;
    private long windowSeconds = 3600;
    private List<RateLimitRule> rules = new ArrayList<>();

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public List<RateLimitRule> getRules() {
        return rules;
    }

    public void setRules(List<RateLimitRule> rules) {
        this.rules = rules;
    }

    public void validate() {
        if (defaultLimit <= 0) {
            throw new IllegalArgumentException("defaultLimit must be greater than 0");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be greater than 0");
        }
        if (rules == null) {
            throw new IllegalArgumentException("rules must not be null");
        }

        Set<String> apiRules = new HashSet<>();
        Set<String> userApiRules = new HashSet<>();
        for (RateLimitRule rule : rules) {
            if (rule == null) {
                throw new IllegalArgumentException("rules must not contain null entries");
            }
            if (rule.getApi() == null || rule.getApi().isBlank()) {
                throw new IllegalArgumentException("rule api must not be blank");
            }
            if (rule.getLimit() <= 0) {
                throw new IllegalArgumentException("rule limit must be greater than 0");
            }

            String apiKey = rule.getApi().trim();
            if (rule.isApiLevelRule()) {
                if (!apiRules.add(apiKey)) {
                    throw new IllegalArgumentException("duplicate API-level rule for " + apiKey);
                }
            } else {
                String userKey = rule.getUserId().trim() + "::" + apiKey;
                if (!userApiRules.add(userKey)) {
                    throw new IllegalArgumentException("duplicate user+api rule for " + userKey);
                }
            }
        }
    }
}
