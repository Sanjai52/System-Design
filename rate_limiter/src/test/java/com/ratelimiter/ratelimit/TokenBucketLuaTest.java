package com.ratelimiter.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketLuaTest {

    private StringRedisTemplate redis;
    private DefaultRedisScript<Long> script;

    @BeforeEach
    void setUp() {
        redis = RedisTestSupport.template();
        script = RedisTestSupport.script();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void fullBucketAllowsCapacityThenRejects() {
        for (int i = 0; i < 10; i++) {
            assertEquals(1L, RedisTestSupport.execute(redis, script, "full"), "request " + (i + 1) + " should be allowed");
        }
        assertEquals(0L, RedisTestSupport.execute(redis, script, "full"), "11th request must be rejected");
        assertEquals(0L, RedisTestSupport.execute(redis, script, "full"), "12th request must be rejected");
    }

    @Test
    void refillsAtRefillRateAfterElapsedTime() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            RedisTestSupport.execute(redis, script, "refill");
        }
        assertEquals(0L, RedisTestSupport.execute(redis, script, "refill"), "bucket drained, next must be rejected");
        long startNanos = System.nanoTime();
        Thread.sleep(3000);
        double elapsedSeconds = (System.nanoTime() - startNanos) / 1e9;
        long expected = Math.max(1, (long) Math.floor(elapsedSeconds * 2.0) - 1);

        long allowed = 0;
        for (int i = 0; i < expected + 3 && RedisTestSupport.execute(redis, script, "refill") == 1L; i++) {
            allowed++;
        }
        assertTrue(allowed >= expected, "refill restored ~rate×elapsed tokens, got " + allowed + " of " + expected);
        assertTrue(allowed <= expected + 3, "refill must stay within slack of rate×elapsed, got " + allowed);
        assertEquals(0L, RedisTestSupport.execute(redis, script, "refill"), "bucket drains again after refill consumption");
    }

    @Test
    void refillIsCappedAtCapacity() throws InterruptedException {
        RedisTestSupport.execute(redis, script, "cap");
        RedisTestSupport.execute(redis, script, "cap");
        Thread.sleep(6000);
        for (int i = 0; i < 10; i++) {
            assertEquals(1L, RedisTestSupport.execute(redis, script, "cap"), "refilled-to-capacity request " + (i + 1));
        }
        assertEquals(0L, RedisTestSupport.execute(redis, script, "cap"), "never exceeds capacity");
    }

    @Test
    void idleKeyExpires() throws InterruptedException {
        String ttlMs = "1000";
        redis.execute(script, List.of("ratelimit:expiring"), RedisTestSupport.CAPACITY, RedisTestSupport.REFILL_RATE, ttlMs);
        Long ttl = redis.getConnectionFactory().getConnection().keyCommands().ttl("ratelimit:expiring".getBytes());
        assertTrue(ttl != null && ttl > 0, "key should carry a TTL");
        Thread.sleep(1500);
        Long after = redis.getConnectionFactory().getConnection().keyCommands().ttl("ratelimit:expiring".getBytes());
        assertEquals(-2L, after, "key must be evicted after TTL");
    }
}