# Rate Limiter — Analysis

Implementation: Spring Boot 3.3 · Redis 7 (Lua scripting) · Java 21.
Config used for all measurements: `bucket-capacity = 10`, `refill-rate = 2.0`
tokens/second.

## 1. Role of Token Bucket, Bucket Capacity, Refill Rate, Redis

- **Token Bucket** — the algorithm that governs admission: each client owns a
  bucket; every admitted request consumes exactly one token, and tokens are
  replenished continuously over time. A request is admitted only when the
  bucket holds ≥ 1 token. It smooths short bursts while enforcing a long-run
  average rate.
- **Bucket Capacity** — the maximum number of tokens a bucket can hold. It sets
  the burst ceiling: a client can fire at most `capacity` requests instantly,
  then must wait for refills. In this lab it is `rate-limiter.bucket-capacity`
  (default 10).
- **Refill Rate** — how fast tokens re-enter the bucket, in tokens/second. It
  sets the sustained limit: once drained, the client is admitted again at
  `refill-rate` requests per second (default 2/s). Refills are proportional to
  elapsed time, not batched per second, so the limit is smooth.
- **Redis** — the shared, fast, in-memory store that holds every bucket
  (`HSET ratelimit:{clientId}` with `tokens` and `last_refill`, plus a PEXPIRE
  idle TTL). It is the single source of truth for all application instances and
  runs the token-bucket logic atomically inside a Lua script, eliminating
  read-modify-write races.

## 2. How the Token Bucket allows or rejects a request

Every request for a client runs the same Lua script:

1. Read the bucket hash from Redis. On the first request (`tokens` missing)
   the bucket is initialized full — `tokens = capacity`.
2. Compute elapsed time from the stored `last_refill` using Redis `TIME`
   (server clock, identical for every instance) and refill:
   `tokens = min(capacity, tokens + elapsed × refillRate)`.
3. If `tokens ≥ 1` → decrement by 1, persist, return **allow** (HTTP 200).
4. Else → persist the refilled-but-insufficient count, return **reject**
   (HTTP 429 with `Retry-After`).

Because steps 1–4 execute inside one Lua script, Redis serializes them: two
concurrent requests can never both see the same last token. The lab
concurrency test (10 threads × 10 requests against a capacity-5 bucket) passed
exactly 5 of 100 requests.

## 3. Why Redis is required with multiple application instances

With a single instance an in-JVM `ConcurrentHashMap` would suffice, but rate
limiting must hold globally:

- **Shared state** — if two instances kept their own buckets, a client could
  split traffic across them and effectively double its limit. Redis is the one
  shared store both instances read and write.
- **Atomicity without locks** — the Lua script is the critical section. With
  per-instance counters, two instances checking "is a token available" at the
  same moment would both admit; Redis serializes the check-and-decrement.
- **Consistent clock** — refill uses Redis `TIME`, so instance clock drift
  cannot inflate or deflate the effective rate.
- **Survival** — buckets survive instance restarts/scale-downs; idle keys
  expire via TTL instead of leaking forever.

## 4. Comparison of rate-limiting algorithms

| Algorithm | How it counts | Burst handling | Memory | Issues |
|---|---|---|---|---|
| **Fixed Window** | one counter per fixed interval (e.g., 1 min), reset on boundary | up to 2× the limit at window edges (request at 59 s + request at 0 s) | 1 counter/window | boundary double-count; uneven |
| **Sliding Window Log** | timestamps of every request within the window | exact — no boundary gap | O(n) per client (all timestamps) | high memory, expensive deletes |
| **Sliding Window Counter** | weighted blend of current + previous window counters | near-exact, approximate | 2 counters/window | approximation error |
| **Token Bucket (this lab)** | continuous token refill, no fixed boundary | exact `capacity` burst, smooth sustained rate | 1 small hash/client | needs a store + atomic update in a cluster |

Token Bucket is the standard choice for APIs: memory is constant per client,
bursts are controlled precisely by `capacity`, and the sustained rate by
`refill-rate` — with no window-boundary artifacts.

## 5. Behaviour under normal and burst traffic (observed)

Measured with `scripts/burst_test.py` against the live app (capacity 10,
refill 2/s):

**Normal traffic — 2 requests/s for 15 s (29 requests):**
29 allowed (200), 0 blocked — 100% pass rate. Steady users comfortably stay
under the refill rate, so the bucket hovers near full and no request is ever
rejected.

**Burst traffic — 50 requests/s for 5 s (250 requests):**
18 allowed, 232 blocked (429) — 7.2% pass rate. The bucket's 10 initial tokens
admit the first 10 instantaneous requests; then admission continues only at
the refill pace (10 + ~2/s × ~4 effective seconds ≈ 18). The burst is
absorbed smoothly: backend load is capped at capacity + refill rate instead of
the client's 50/s.

**Recovery — after a 3 s pause, 5 requests:** 5 allowed, 0 blocked — 100%
pass rate. With the burst over, the bucket refilled (3 s × 2/s = 6 tokens) and
normal clients are unaffected.

**Parallel curl burst (15 concurrent requests):** 11 × 200 then 4 × 429; the
next request returned `HTTP/1.1 429` with `Retry-After: 1`, demonstrating the
client-facing contract.

**Takeaway:** token bucket protects the backend — bursts beyond capacity are
rejected (429) rather than queueing, sustained load is pinned to the refill
rate, and honest traffic experiences no penalty.
