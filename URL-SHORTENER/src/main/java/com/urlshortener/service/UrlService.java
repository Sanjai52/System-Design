package com.urlshortener.service;

import com.urlshortener.model.Url;
import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    private final UrlRepository urlRepository;
    private final HashService hashService;

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
                    return urlRepository.save(url);
                });
        long elapsed = System.nanoTime() - start;
        log.info("createShortUrl(longUrl={}) -> shortCode={} | DB time: {} ms",
                longUrl, result.getShortCode(), String.format("%.3f", elapsed / 1_000_000.0));
        return result;
    }

    public String getOriginalUrl(String shortCode) {
        long start = System.nanoTime();
        String longUrl = urlRepository.findByShortCode(shortCode)
                .map(Url::getLongUrl)
                .orElse(null);
        long elapsed = System.nanoTime() - start;
        log.info("getOriginalUrl(shortCode={}) -> {} | DB time: {} ms",
                shortCode, longUrl != null ? "HIT" : "MISS", String.format("%.3f", elapsed / 1_000_000.0));
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
