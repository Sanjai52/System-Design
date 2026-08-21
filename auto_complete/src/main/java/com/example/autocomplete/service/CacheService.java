package com.example.autocomplete.service;

import com.example.autocomplete.model.SearchTerm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class CacheService {

    private static final String CACHE_PREFIX = "autocomplete:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${autocomplete.cache.ttl-seconds:600}")
    private long ttlSeconds;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @SuppressWarnings("unchecked")
    public List<SearchTerm> getCachedSuggestions(String prefix) {
        String key = CACHE_PREFIX + prefix.toLowerCase();
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List) {
            return (List<SearchTerm>) cached;
        }
        return null;
    }

    public void cacheSuggestions(String prefix, List<SearchTerm> suggestions) {
        String key = CACHE_PREFIX + prefix.toLowerCase();
        redisTemplate.opsForValue().set(key, suggestions, ttlSeconds, TimeUnit.SECONDS);
    }
}
