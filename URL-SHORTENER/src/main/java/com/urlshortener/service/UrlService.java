package com.urlshortener.service;

import com.urlshortener.model.Url;
import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    private final UrlRepository urlRepository;
    private final HashService hashService;
    private final StringRedisTemplate redisTemplate;

    private static final long CACHE_TTL_HOURS = 24;

    public Url createShortUrl(String longUrl) {
        long start = System.nanoTime();
        Url result = urlRepository.findByLongUrl(longUrl)
                .orElseGet(() -> {
                    String shortCode = generateUniqueShortCode(longUrl);
                    Url url = Url.builder()
                            .shortCode(shortCode)
                            .longUrl(longUrl)
                            .createdAt(LocalDateTime.now())
                            .build();
                    Url saved = urlRepository.save(url);
                    redisTemplate.opsForValue().set(shortCode, longUrl, CACHE_TTL_HOURS, TimeUnit.HOURS);
                    return saved;
                });
        long elapsed = System.nanoTime() - start;
        log.info("createShortUrl(longUrl={}) -> shortCode={} | total: {} ms",
                longUrl, result.getShortCode(), String.format("%.3f", elapsed / 1_000_000.0));
        return result;
    }

    public String getOriginalUrl(String shortCode) {
        long redisStart = System.nanoTime();
        String cachedUrl = redisTemplate.opsForValue().get(shortCode);
        long redisTime = System.nanoTime() - redisStart;

        if (cachedUrl != null) {
            log.info("getOriginalUrl(shortCode={}) -> CACHE HIT | Redis: {} ms",
                    shortCode, String.format("%.3f", redisTime / 1_000_000.0));
            return cachedUrl;
        }

        long dbStart = System.nanoTime();
        String longUrl = urlRepository.findByShortCode(shortCode)
                .map(url -> {
                    redisTemplate.opsForValue().set(shortCode, url.getLongUrl(), CACHE_TTL_HOURS, TimeUnit.HOURS);
                    return url.getLongUrl();
                })
                .orElse(null);
        long dbTime = System.nanoTime() - dbStart;

        log.info("getOriginalUrl(shortCode={}) -> {} | Redis: {} ms, DB: {} ms",
                shortCode, longUrl != null ? "CACHE MISS" : "MISS",
                String.format("%.3f", redisTime / 1_000_000.0),
                String.format("%.3f", dbTime / 1_000_000.0));
        return longUrl;
    }

    private String generateUniqueShortCode(String longUrl) {
        String shortCode = hashService.generateShortCode(longUrl);
        int iteration = 0;

        while (urlRepository.existsByShortCode(shortCode)) {
            Url existing = urlRepository.findByShortCode(shortCode).orElse(null);
            if (existing != null && existing.getLongUrl().equals(longUrl)) {
                return shortCode;
            }
            iteration++;
            shortCode = hashService.generateShortCodeWithSalt(longUrl, iteration);
        }

        return shortCode;
    }

}
