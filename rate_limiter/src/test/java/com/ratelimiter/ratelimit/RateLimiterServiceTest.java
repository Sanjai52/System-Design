package com.ratelimiter.ratelimit;

import com.ratelimiter.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterServiceTest {

    private StringRedisTemplate redis;
    private RateLimiterService service;
    private ClientIdentifier clientIdentifier;

    @BeforeEach
    void setUp() {
        redis = RedisTestSupport.template();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        RateLimitProperties properties = new RateLimitProperties();
        properties.setBucketCapacity(5);
        properties.setRefillRate(1.0);
        properties.setIdleTtlSeconds(3600);
        service = new RateLimiterService(redis, properties);
        clientIdentifier = new ClientIdentifier();
    }

    @Test
    void allowsUpToCapacityThenRejects() {
        for (int i = 0; i < 5; i++) {
            assertTrue(service.tryAcquire("client-a"), "request " + (i + 1) + " allowed");
        }
        assertFalse(service.tryAcquire("client-a"), "6th request rejected");
        assertFalse(service.tryAcquire("client-a"), "7th request rejected");
        assertTrue(service.tryAcquire("client-b"), "independent bucket for another client");
    }

    @Test
    void concurrentBurstNeverExceedsCapacity() throws Exception {
        RateLimitProperties zeroRefill = new RateLimitProperties();
        zeroRefill.setBucketCapacity(5);
        zeroRefill.setRefillRate(0.0);
        zeroRefill.setIdleTtlSeconds(3600);
        RateLimiterService noRefill = new RateLimiterService(redis, zeroRefill);

        int threads = 10;
        int perThread = 10;
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < perThread; i++) {
                tasks.add(() -> noRefill.tryAcquire("burst"));
            }
        }
        var pool = Executors.newFixedThreadPool(threads);
        List<Boolean> results = pool.invokeAll(tasks).stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        long allowed = results.stream().filter(b -> b).count();
        assertEquals(5, allowed, "at most capacity requests win a 100-request burst");
    }

    @Test
    void resolvesClientIdFromHeaderOrIp() {
        MockHttpServletRequest withHeader = new MockHttpServletRequest();
        withHeader.addHeader(ClientIdentifier.CLIENT_ID_HEADER, "alice-42");
        assertEquals("alice-42", clientIdentifier.resolve(withHeader));

        MockHttpServletRequest blankHeader = new MockHttpServletRequest();
        blankHeader.addHeader(ClientIdentifier.CLIENT_ID_HEADER, "   ");
        blankHeader.setRemoteAddr("10.0.0.7");
        assertEquals("10.0.0.7", clientIdentifier.resolve(blankHeader));

        MockHttpServletRequest noHeader = new MockHttpServletRequest();
        noHeader.setRemoteAddr("10.0.0.8");
        assertEquals("10.0.0.8", clientIdentifier.resolve(noHeader));
    }

    @Test
    void retryAfterReflectsTimeUntilNextToken() {
        for (int i = 0; i < 5; i++) {
            service.tryAcquire("retry");
        }
        assertTrue(service.retryAfterSeconds("retry") >= 1, "retry-after at least 1s");
        assertNotNull(service.status("retry").get("tokens"));
    }
}