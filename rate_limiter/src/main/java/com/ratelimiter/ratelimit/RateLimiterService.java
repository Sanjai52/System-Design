package com.ratelimiter.ratelimit;

import com.ratelimiter.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    private static final DefaultRedisScript<Long> ALLOW_SCRIPT = new DefaultRedisScript<>(RateLimitLua.SCRIPT, Long.class);

    private static final DefaultRedisScript<String> TIME_SCRIPT = new DefaultRedisScript<>(
            "local t = redis.call('TIME'); return t[1] .. '.' .. t[2]", String.class);

    public boolean tryAcquire(String clientId) {
        Long allowed = redis.execute(ALLOW_SCRIPT, List.of(key(clientId)),
                String.valueOf(properties.getBucketCapacity()),
                String.valueOf(properties.getRefillRate()),
                String.valueOf(properties.getIdleTtlSeconds() * 1000));
        return allowed != null && allowed == 1L;
    }

    public long retryAfterSeconds(String clientId) {
        Double tokens = currentTokens(clientId);
        if (tokens == null) {
            return 0;
        }
        double missing = 1.0 - (tokens - Math.floor(tokens));
        long seconds = (long) Math.ceil(missing / properties.getRefillRate());
        return Math.max(1, seconds);
    }

    public Map<String, Object> status(String clientId) {
        Map<Object, Object> bucket = redis.opsForHash().entries(key(clientId));
        Map<String, Object> status = new HashMap<>();
        status.put("clientId", clientId);
        status.put("capacity", properties.getBucketCapacity());
        status.put("refillRate", properties.getRefillRate());
        status.put("idleTtlSeconds", properties.getIdleTtlSeconds());
        if (bucket.isEmpty()) {
            status.put("tokens", (double) properties.getBucketCapacity());
            status.put("lastRefill", null);
        } else {
            double tokens = Double.parseDouble((String) bucket.get("tokens"));
            double lastRefill = Double.parseDouble((String) bucket.get("last_refill"));
            tokens = Math.min(properties.getBucketCapacity(),
                    tokens + Math.max(0, nowSeconds() - lastRefill) * properties.getRefillRate());
            status.put("tokens", tokens);
            status.put("lastRefill", lastRefill);
        }
        return status;
    }

    private double nowSeconds() {
        return Double.parseDouble(redis.execute(TIME_SCRIPT, List.of()));
    }

    private Double currentTokens(String clientId) {
        Object tokens = redis.opsForHash().get(key(clientId), "tokens");
        return tokens == null ? null : Double.parseDouble((String) tokens);
    }

    private String key(String clientId) {
        return "ratelimit:" + clientId;
    }
}