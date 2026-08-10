package com.ratelimiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimitProperties {

    private long bucketCapacity = 10;

    private double refillRate = 2.0;

    private long idleTtlSeconds = 86400;
}