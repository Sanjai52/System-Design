package com.ratelimiter.ratelimit;

public final class RateLimitLua {

    public static final String SCRIPT = """
            local tokens = redis.call('HGET', KEYS[1], 'tokens')
            local lastRefill = redis.call('HGET', KEYS[1], 'last_refill')
            local capacity = tonumber(ARGV[1])
            local refillRate = tonumber(ARGV[2])
            local now = tonumber(redis.call('TIME')[1] .. '.' .. redis.call('TIME')[2])
            if not tokens then
                tokens = capacity
                lastRefill = now
            else
                tokens = tonumber(tokens)
                lastRefill = tonumber(lastRefill)
                local elapsed = now - lastRefill
                if elapsed > 0 then
                    tokens = math.min(capacity, tokens + elapsed * refillRate)
                end
            end
            local allowed = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            end
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'last_refill', now)
            redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[3]))
            return allowed
            """;

    private RateLimitLua() {
    }
}