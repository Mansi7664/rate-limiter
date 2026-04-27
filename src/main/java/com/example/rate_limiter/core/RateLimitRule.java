package com.example.rate_limiter.core;

public class RateLimitRule {
    private String userId;
    private String api;
    private int limit;

    public RateLimitRule() {
    }

    public RateLimitRule(String userId, String api, int limit) {
        this.userId = userId;
        this.api = api;
        this.limit = limit;
    }

    public static RateLimitRule forApi(String api, int limit) {
        return new RateLimitRule(null, api, limit);
    }

    public static RateLimitRule forUserApi(String userId, String api, int limit) {
        return new RateLimitRule(userId, api, limit);
    }

    public boolean isApiLevelRule() {
        return userId == null || userId.isBlank();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
