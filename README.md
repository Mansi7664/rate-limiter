# Rate Limiter

Framework-agnostic Java rate limiter built for the API rate-limit machine coding problem.

## Problem Coverage

- Supports a global default limit.
- Supports API-level limits that apply to all users for a given API.
- Supports user+API overrides that take precedence over API-level rules.
- Tracks requests by the `userId + api` combination.
- Uses a fixed-window strategy with an in-memory store.

Rule precedence is:

1. Exact `userId + api` rule
2. API-level rule
3. Global default

## Approach

The solution is split into small interfaces and implementations so it can be plugged into any web framework:

- `RateLimiter` exposes the single `allowRequest(userId, api)` operation.
- `RateLimitService` coordinates rule lookup and request counting.
- `RateLimitRuleResolver` resolves which limit applies for the current request.
- `RateLimitStore` tracks request counts inside a time window.
- `InMemoryRateLimitStore` is the included store implementation for this submission.

Each request is processed using the tuple `(userId, api)`. The resolver first checks for an exact override, then falls back to the API-level limit, and finally uses the global default if no specific configuration exists.

## Data Storage Format

The in-memory store keeps a `ConcurrentHashMap<String, Window>`.

- The key is `rate:{userId}:{api}`.
- The value stores the current request count and the window expiry timestamp.
- Expired entries are evicted during subsequent requests to prevent stale counters from accumulating forever.

This structure is simple, thread-safe for a single JVM process, and easy to explain in an interview.

### Alternatives Considered

- `Redis` with TTL keys:
  Better for distributed systems and multiple application nodes, but adds infrastructure dependency.
- Sliding-window log:
  More accurate around window boundaries, but more expensive in memory and cleanup cost.
- Token bucket / leaky bucket:
  Better for smoothing bursts, but slightly more complex than required for this exercise.

## Assumptions

- A request belongs to exactly one user and one API path.
- Limits are positive integers.
- The configured window is the same duration for all rules in this version.
- This submission targets a single-process deployment using in-memory storage.

## Inputs That Could Improve Accuracy

- HTTP method, so `GET /reports` and `POST /reports` can be limited differently.
- Client application or API key in addition to user id.
- Cost or weight per API call, for weighted limiting of heavier endpoints.
- Tenant or account id for multi-tenant throttling.
- Burst limit and sustained limit as separate values.

## Future Improvements

- Add a distributed `RateLimitStore` implementation backed by Redis.
- Support different windows per API or per rule.
- Return a richer response object with remaining quota and retry-after time.
- Add metrics, audit logging, and configuration reloading.
- Switch to a sliding-window or token-bucket algorithm if burst fairness becomes important.

## Build And Run

### Requirements

- JDK 17
- Maven 3.8+

### Compile

```bash
mvn clean package
```

### Run the demo

```bash
java -jar target/rate-limiter-1.0.0.jar
```

### Run the self-tests

```bash
java -cp target/classes com.example.rate_limiter.demo.SelfTestRunner
```

### Windows shortcut

```bat
run.bat
```

## IDE Setup

### IntelliJ IDEA

1. Open IntelliJ IDEA.
2. Select `Open` and choose the project root folder.
3. Let IntelliJ import the Maven project from `pom.xml`.
4. Use `RateLimiterApplication` or `SelfTestRunner` as the run configuration.

### Eclipse

1. Open Eclipse.
2. Select `File -> Import -> Existing Maven Projects`.
3. Choose the project root folder and finish the import.
4. Run `RateLimiterApplication` or `SelfTestRunner` as a Java application.

## Example Configuration

```java
RateLimitConfig config = new RateLimitConfig();
config.setDefaultLimit(100);
config.setWindowSeconds(3600);
config.setRules(List.of(
        RateLimitRule.forApi("/api/v1/developers", 80),
        RateLimitRule.forApi("/api/v1/organizations", 300),
        RateLimitRule.forUserApi("user1", "/api/v1/developers", 100),
        RateLimitRule.forUserApi("user2", "/api/v1/developers", 50),
        RateLimitRule.forUserApi("user2", "/api/v1/organizations", 500)
));
```

## Execution Flow

1. The caller invokes `allowRequest(userId, api)`.
2. The resolver determines the applicable limit using rule precedence.
3. The store increments the counter for that `userId + api` key in the current time window.
4. The limiter returns `true` if the count is within the limit, otherwise `false`.

## External Dependencies

No external server or separate application is required for this submission. The solution runs entirely in-process using Java and Maven.
