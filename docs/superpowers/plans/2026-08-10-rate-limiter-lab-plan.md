# Rate Limiter Lab — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` (recommended) or `executing-plans` to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** Build a Spring Boot REST API protected by a Token-Bucket rate limiter; bucket state (tokens + last refill) lives in Redis, updated atomically via a Lua script so the limit holds across multiple application instances; then generate normal + burst traffic, observe 429 behavior, and answer the 5 analysis questions from the lab PDF (`rate_limiter/EXP 6-Rate Limiter.pdf`).

**Architecture:** A `RateLimitInterceptor` on `/api/**` resolves the client via `X-Client-Id` header (fallback: remote IP). `RateLimiterService.tryAcquire(clientId)` executes an atomic Lua script against Redis: refills tokens by `elapsed × refillRate` (capped at capacity, using Redis `TIME` as the clock), permits if ≥1 token remains, else returns 0 → interceptor aborts → `GlobalExceptionHandler` replies 429 + `Retry-After`. Bucket key `ratelimit:{clientId}` (HSET `tokens`, `last_refill`) with PEXPIRE idle TTL.

**Tech Stack:** Java 21 · Spring Boot 3.3 · Maven · `spring-boot-starter-data-redis` · Lombok · springdoc-openapi · JUnit 5 · Docker Compose (`redis:7-alpine` on 6379). Mirror `URL-SHORTENER/pom.xml`.

**Global Constraints:** Package `com.ratelimiter`. Config via `rate-limiter.bucket-capacity` / `.refill-rate` / `.idle-ttl-seconds` (defaults 10 / 2.0 / 86400). All bucket mutation in ONE Lua script. `Retry-After` = seconds until next token. Project folder `rate_limiter/`. Commit per completed task.

## Core API

```java
// config/RateLimitProperties.java  @ConfigurationProperties("rate-limiter") — bucketCapacity, refillRate, idleTtlSeconds
// ratelimit/RateLimitLua.java      public static final String SCRIPT (KEYS[1]=bucket, ARGV[1]=capacity, ARGV[2]=refillRate, ARGV[3]=ttlMs)
// ratelimit/ClientIdentifier.java  String resolve(HttpServletRequest)   // X-Client-Id header → remoteAddr
// ratelimit/RateLimiterService.java  boolean tryAcquire(String clientId); long retryAfterSeconds(String clientId); Map<String,Object> status(String clientId);
// interceptor/RateLimitInterceptor.java  preHandle on /api/** (throws RateLimitExceededException)
// exception/RateLimitExceededException + GlobalExceptionHandler → 429 + Retry-After
// controller/DemoController.java   GET /api/demo (protected), GET /api/ratelimit/status (bucket state)
```

---

### Task 1: Bootstrap Maven project

- [x] **Step 1:** `pom.xml` — web, data-redis, lombok, springdoc 2.6.0, spring-boot-starter-test; java 21.
- [x] **Step 2:** `mvn -q compile` passes.
- [x] **Step 3:** `application.properties` — redis host/port, `rate-limiter.*`, springdoc paths.
- [x] **Step 4:** `RateLimiterApplication.java` (`@SpringBootApplication @ConfigurationPropertiesScan`).
- [x] **Step 5:** stub `static/index.html`.

### Task 2: Lua token bucket

- [x] **Step 1:** `TokenBucketLuaTest` — capacity then reject; refill after elapsed time; refill capped at capacity; idle TTL eviction.
- [x] **Step 2:** tests fail (classes missing).
- [x] **Step 3:** `RateLimitLua` + `RateLimitProperties`; Lua uses fractional Redis `TIME` for smooth refills.
- [x] **Step 4:** tests pass against compose Redis (Testcontainers dropped — Docker Desktop 29.5 npipe returns HTTP 400 to docker-java; tests use `REDIS_HOST`/`REDIS_PORT`).
- [x] **Step 5:** commit.

### Task 3: Service layer

- [x] **Step 1:** `RateLimiterServiceTest` — capacity gate; 100-request concurrent burst (10 threads × 10) ≤ capacity; `ClientIdentifier` header/IP; retryAfter ≥ 1s.
- [x] **Step 2:** run failing.
- [x] **Step 3:** `ClientIdentifier`, `RateLimiterService` (Lua execution + status/retryAfter).
- [x] **Step 4:** run passing.
- [x] **Step 5:** commit. (Also fixed: Lombok 1.18.46 + `annotationProcessorPaths` for JDK 24; byte-buddy 1.18.11 for Mockito.)

### Task 4 + 5: HTTP layer, controller, UI

- [x] **Step 1:** `RateLimitInterceptor`, `RateLimitExceededException`, `GlobalExceptionHandler` (429 + Retry-After), `WebConfig` interceptor registration.
- [x] **Step 2:** `DemoController` — `/api/demo`, `/api/ratelimit/status`.
- [x] **Step 3:** `RateLimitHttpTest` (MockMvc) — 200 while allowed, 429 + Retry-After when blocked, IP fallback, status endpoint.
- [x] **Step 4:** `index.html` UI — client-id input, hit/burst buttons, 200/429 counters, live bucket gauge.
- [x] **Step 5:** commit.

### Task 6: Docker Compose + load script + README

- [x] **Step 1:** `docker-compose.yml` — `redis:7-alpine`, port 6379, named volume.
- [x] **Step 2:** `scripts/burst_test.py` — normal phase (2 req/s, 15 s), burst phase (50 req/s, 5 s, threaded), recovery check; stdlib only.
- [x] **Step 3:** `README.md` — architecture, quick start, config table, API table, load test usage, tests.
- [x] **Step 4:** commit.

### Task 7: Integration verification

- [x] **Step 1:** `docker compose up -d` → Redis PONG.
- [x] **Step 2:** `mvn test` — 12 tests green.
- [x] **Step 3:** `mvn spring-boot:run` — app starts.
- [x] **Step 4:** curl: 200s while tokens; parallel 15-request burst → 11×200 + 4×429, `Retry-After: 1`.
- [x] **Step 5:** `burst_test.py` — normal 29/29 (100%), burst 18/250 (7.2%), recovery 5/5 (100%).
- [x] **Step 6:** commit any fixes.

### Task 8: Analysis write-up

- [x] **Step 1:** `docs/analysis.md` — answers to the 5 questions with observed numbers.
- [x] **Step 2:** root `README.md` table row for `rate_limiter`.
- [x] **Step 3:** commit.

## Self-Review
- Spec coverage: REST API ✓, Redis config ✓, clientId/IP ✓, configurable capacity/refill ✓, Redis storage ✓, 429 ✓, normal+burst generation ✓, concurrent + Lua atomicity ✓, analysis Q1–5 ✓.
- Environment quirks documented: Docker Desktop 29.5 blocks docker-java on npipe (Testcontainers → compose Redis); JDK 24 needs Lombok ≥1.18.46 + explicit `annotationProcessorPaths` and byte-buddy ≥1.18.11.
