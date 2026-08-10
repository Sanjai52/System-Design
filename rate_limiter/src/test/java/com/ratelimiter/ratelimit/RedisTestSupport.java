package com.ratelimiter.ratelimit;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

final class RedisTestSupport {

    static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

    static final String CAPACITY = "10";
    static final String REFILL_RATE = "2.0";
    static final String TTL_MS = "86400000";

    private RedisTestSupport() {
    }

    static StringRedisTemplate template() {
        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS_HOST, REDIS_PORT));
        factory.afterPropertiesSet();
        var redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        return redis;
    }

    static DefaultRedisScript<Long> script() {
        return new DefaultRedisScript<>(RateLimitLua.SCRIPT, Long.class);
    }

    static long execute(StringRedisTemplate redis, DefaultRedisScript<Long> script, String key) {
        Long result = redis.execute(script, List.of("ratelimit:" + key), CAPACITY, REFILL_RATE, TTL_MS);
        return result == null ? -1 : result;
    }
}