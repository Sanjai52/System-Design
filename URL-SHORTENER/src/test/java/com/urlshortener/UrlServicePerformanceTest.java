package com.urlshortener;

import com.urlshortener.model.Url;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.service.HashService;
import com.urlshortener.service.UrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class UrlServicePerformanceTest {

    @Autowired
    private UrlService urlService;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final int ITERATIONS = 100;
    private final List<String> shortCodes = new ArrayList<>();

    @Test
    void compareCacheHitVsMiss() {
        String longUrl = "https://example.com/" + System.currentTimeMillis();
        String shortCode = urlService.createShortUrl(longUrl).getShortCode();

        redisTemplate.delete(shortCode);

        long totalMiss = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            urlService.getOriginalUrl(shortCode);
            totalMiss += System.nanoTime() - start;
        }
        double avgMissMs = (totalMiss / (double) ITERATIONS) / 1_000_000.0;

        long totalHit = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            urlService.getOriginalUrl(shortCode);
            totalHit += System.nanoTime() - start;
        }
        double avgHitMs = (totalHit / (double) ITERATIONS) / 1_000_000.0;

        System.out.println("\n========== Performance Comparison ==========");
        System.out.printf("Cache Miss (DB)  - Avg: %.3f ms over %d iterations%n", avgMissMs, ITERATIONS);
        System.out.printf("Cache Hit (Redis) - Avg: %.3f ms over %d iterations%n", avgHitMs, ITERATIONS);
        System.out.printf("Speedup: %.1fx%n", avgMissMs / avgHitMs);
        System.out.println("=============================================\n");
    }
}