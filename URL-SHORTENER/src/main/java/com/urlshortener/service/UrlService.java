package com.urlshortener.service;

import com.urlshortener.model.Url;
import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;
    private final HashService hashService;
    private final StringRedisTemplate redisTemplate;

    private static final long CACHE_TTL_HOURS = 24;

    public Url createShortUrl(String longUrl) {
        return urlRepository.findByLongUrl(longUrl)
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
    }

    public String getOriginalUrl(String shortCode) {
        String cachedUrl = redisTemplate.opsForValue().get(shortCode);
        if (cachedUrl != null) {
            return cachedUrl;
        }
        return urlRepository.findByShortCode(shortCode)
                .map(url -> {
                    redisTemplate.opsForValue().set(shortCode, url.getLongUrl(), CACHE_TTL_HOURS, TimeUnit.HOURS);
                    return url.getLongUrl();
                })
                .orElse(null);
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
