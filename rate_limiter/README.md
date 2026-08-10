# API Rate Limiter — Token Bucket with Redis (Lab Exercise 6)

A Spring Boot REST API protected by a **Token Bucket** rate limiter. Bucket state
(tokens + last refill time) lives in **Redis** and is updated **atomically via a
Lua script**, so the limit holds correctly across multiple application instances.

## How it works

```
                  ┌───────────────────────────────────────────┐
   client ───────►│ RateLimitInterceptor  (preHandle /api/**)  │
   X-Client-Id    │    ClientIdentifier: X-Client-Id or IP     │
   or IP          └───────────────────┬───────────────────────┘
                                      │ tryAcquire(clientId)
                                      ▼
                    ┌──────────────────────────────────────────┐
                    │ RateLimiterService ── executes Lua script │
                    │ KEYS[1] = ratelimit:{clientId}           │
                    │ HSET tokens, last_refill  (redis TIME)   │
                    │ refill = min(capacity, tokens + Δt·rate) │
                    │ allow if tokens >= 1  else reject        │
                    └──────────────────────────────────────────┘
           allowed ───► controller    rejected ───► 429 + Retry-After
```

- **Token Bucket**: each client has a bucket holding up to `capacity` tokens;
  every request spends 1 token, tokens refill continuously at `refillRate`.
- **Atomicity**: the check-and-update runs inside one Redis Lua script — no
  read-modify-write race, even under concurrency.
- **Redis**: shared bucket state across instances; the server clock (`TIME`)
  drives refill so instance clocks can't skew the limit.
- **Clients**: identified by `X-Client-Id` header, falling back to remote IP.

## Quick start

```bash
docker compose up -d              # start Redis 7 (localhost:6379)
mvn spring-boot:run               # app on http://localhost:8080
```

Open http://localhost:8080 (live UI: send requests, fire a 30-request burst,
watch the bucket gauge), or use the API directly.

## Configuration

| Property | Env | Default | Meaning |
|---|---|---|---|
| `rate-limiter.bucket-capacity` | `RATE_LIMIT_CAPACITY` | 10 | max tokens in a bucket |
| `rate-limiter.refill-rate` | `RATE_LIMIT_REFILL_RATE` | 2.0 | tokens added per second |
| `rate-limiter.idle-ttl-seconds` | `RATE_LIMIT_IDLE_TTL` | 86400 | evict idle client keys |

## API

| Method | Path | Description |
|---|---|---|
| GET | `/api/demo` | rate-limited demo endpoint (200, or 429 + `Retry-After`) |
| GET | `/api/ratelimit/status` | current bucket state for the calling client |
| GET | `/swagger-ui.html` | OpenAPI docs |
| GET | `/` | demo UI |

## Load test (normal + burst)

```bash
python scripts/burst_test.py                      # defaults: 2/s normal, 50/s burst
python scripts/burst_test.py --burst-rate 100 --normal-duration 20
```

Reports pass rates per phase — normal traffic should pass at ~100%, the burst
should be capped at the configured capacity, and the recovery phase should pass
again after the bucket refills.

## Tests

```bash
docker compose up -d   # tests hit the compose Redis on 127.0.0.1:6379
mvn test
```

- `TokenBucketLuaTest` — bucket semantics of the Lua script (capacity, refill,
  capping, TTL).
- `RateLimiterServiceTest` — service behavior + **100-request concurrent burst
  never exceeds capacity**.
- `RateLimitHttpTest` — MockMvc: 429 + `Retry-After` wiring.
