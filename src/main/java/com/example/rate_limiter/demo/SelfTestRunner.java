package com.example.rate_limiter.demo;

import com.example.rate_limiter.config.RateLimitConfig;
import com.example.rate_limiter.core.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SelfTestRunner {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        run("user-specific rule overrides api-level rule", SelfTestRunner::userSpecificRuleOverridesApiRule);
        run("api-level rule applies when no user override exists", SelfTestRunner::apiLevelRuleApplies);
        run("global default applies when api-specific rule is missing", SelfTestRunner::globalDefaultApplies);
        run("limit is enforced for a user and api pair", SelfTestRunner::limitIsEnforced);
        run("PDF table limits are enforced for all four user+api pairs", SelfTestRunner::pdfTableLimitsAreEnforced);
        run("different users are isolated for the same api", SelfTestRunner::usersAreIsolated);
        run("different apis are isolated for the same user", SelfTestRunner::apisAreIsolated);
        run("window resets after expiry", SelfTestRunner::windowResetsAfterExpiry);
        run("expired windows are evicted during subsequent requests", SelfTestRunner::expiredWindowsAreEvicted);
        run("invalid configuration is rejected", SelfTestRunner::invalidConfigurationIsRejected);
        run("concurrent requests do not exceed the configured limit", SelfTestRunner::concurrentRequestsRespectLimit);

        System.out.printf("Tests passed: %d, failed: %d%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void run(String name, Check check) {
        try {
            if (check.ok()) {
                passed++;
                System.out.println("PASS: " + name);
            } else {
                failed++;
                System.out.println("FAIL: " + name);
            }
        } catch (Throwable t) {
            failed++;
            System.out.println("FAIL: " + name + " -> " + t.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static boolean userSpecificRuleOverridesApiRule() {
        RateLimiter limiter = newLimiter(baseConfig(), new InMemoryRateLimitStore());
        for (int i = 0; i < 100; i++) {
            require(limiter.allowRequest("user1", "/api/v1/developers"), "user1 should get the higher user-specific limit");
        }
        return !limiter.allowRequest("user1", "/api/v1/developers");
    }

    private static boolean apiLevelRuleApplies() {
        RateLimiter limiter = newLimiter(baseConfig(), new InMemoryRateLimitStore());
        for (int i = 0; i < 80; i++) {
            require(limiter.allowRequest("user3", "/api/v1/developers"), "user3 should use the api-level limit");
        }
        return !limiter.allowRequest("user3", "/api/v1/developers");
    }

    private static boolean globalDefaultApplies() {
        RateLimiter limiter = newLimiter(baseConfig(), new InMemoryRateLimitStore());
        for (int i = 0; i < 100; i++) {
            require(limiter.allowRequest("user9", "/api/v1/unknown"), "unknown api should use the global default");
        }
        return !limiter.allowRequest("user9", "/api/v1/unknown");
    }


    private static boolean limitIsEnforced() {
        RateLimiter limiter = newLimiter(baseConfig(), new InMemoryRateLimitStore());
        for (int i = 0; i < 50; i++) {
            require(limiter.allowRequest("user2", "/api/v1/developers"), "request " + (i + 1) + " should pass");
        }
        return !limiter.allowRequest("user2", "/api/v1/developers");
    }

    private static boolean pdfTableLimitsAreEnforced() {
        RateLimiter limiter = newLimiter(baseConfig(), new InMemoryRateLimitStore());

        Object[][] cases = {
                {"user1", "/api/v1/developers",    100},
                {"user2", "/api/v1/developers",     50},
                {"user1", "/api/v1/organizations", 250},
                {"user2", "/api/v1/organizations", 500}
        };

        for (Object[] c : cases) {
            String user = (String) c[0];
            String api = (String) c[1];
            int limit = (int) c[2];

            for (int i = 1; i <= limit; i++) {
                require(limiter.allowRequest(user, api),
                        user + " " + api + " request " + i + " should pass");
            }

            int overflow = limit + 1;
            if (limiter.allowRequest(user, api)) {
                return false;
            }
            System.out.println("  -> " + user + " " + api
                    + " request " + overflow + " rejected with HTTP 429");
        }

        return true;
    }

    private static boolean usersAreIsolated() {
        RateLimiter limiter = newLimiter(baseConfig(), new InMemoryRateLimitStore());
        for (int i = 0; i < 50; i++) {
            require(limiter.allowRequest("user2", "/api/v1/developers"), "user2 request " + (i + 1) + " should pass");
        }
        require(!limiter.allowRequest("user2", "/api/v1/developers"), "user2 should now be blocked");
        return limiter.allowRequest("user3", "/api/v1/developers");
    }

    private static boolean apisAreIsolated() {
        RateLimiter limiter = newLimiter(baseConfig(), new InMemoryRateLimitStore());
        for (int i = 0; i < 50; i++) {
            require(limiter.allowRequest("user2", "/api/v1/developers"), "developers limit should be consumed independently");
        }
        require(!limiter.allowRequest("user2", "/api/v1/developers"), "developers should now be blocked");
        return limiter.allowRequest("user2", "/api/v1/organizations");
    }

    private static boolean windowResetsAfterExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        RateLimitConfig config = new RateLimitConfig();
        config.setDefaultLimit(2);
        config.setWindowSeconds(5);

        RateLimiter limiter = newLimiter(config, new InMemoryRateLimitStore(clock));
        require(limiter.allowRequest("user1", "/api/v1/ping"), "first request should pass");
        require(limiter.allowRequest("user1", "/api/v1/ping"), "second request should pass");
        require(!limiter.allowRequest("user1", "/api/v1/ping"), "third request should fail in the same window");

        clock.advanceSeconds(6);
        return limiter.allowRequest("user1", "/api/v1/ping");
    }

    private static boolean expiredWindowsAreEvicted() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        InMemoryRateLimitStore store = new InMemoryRateLimitStore(clock, 1);

        store.increment("rate:user1:/api/v1/developers", 5);
        store.increment("rate:user2:/api/v1/developers", 5);
        require(store.activeWindowCount() == 2, "two windows should be tracked");

        clock.advanceSeconds(6);
        store.increment("rate:user3:/api/v1/developers", 5);
        return store.activeWindowCount() == 1;
    }

    private static boolean invalidConfigurationIsRejected() {
        RateLimitConfig config = new RateLimitConfig();
        config.setDefaultLimit(0);

        try {
            new ConfiguredRateLimitRuleResolver(config);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean concurrentRequestsRespectLimit() throws Exception {
        RateLimitConfig config = new RateLimitConfig();
        config.setDefaultLimit(100);
        config.setWindowSeconds(60);
        config.setRules(List.of(RateLimitRule.forApi("/api/v1/concurrent", 25)));

        RateLimiter limiter = newLimiter(config, new InMemoryRateLimitStore());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Boolean>> futures = java.util.stream.IntStream.range(0, 100)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return limiter.allowRequest("load-user", "/api/v1/concurrent");
                    }))
                    .toList();
            start.countDown();

            int allowed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    allowed++;
                }
            }
            return allowed == 25;
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static RateLimiter newLimiter(RateLimitConfig config, RateLimitStore store) {
        return new RateLimitService(
                store,
                new ConfiguredRateLimitRuleResolver(config),
                config.getWindowSeconds()
        );
    }

    private static RateLimitConfig baseConfig() {
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
        return config;
    }

    @FunctionalInterface
    private interface Check {
        boolean ok() throws Exception;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zoneId;

        private MutableClock(Instant instant, ZoneId zoneId) {
            this.instant = instant;
            this.zoneId = zoneId;
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
