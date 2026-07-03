package com.urlshortener.service;

import com.urlshortener.model.Url;
import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;
    private final HashService hashService;

    public Url createShortUrl(String longUrl) {
        return urlRepository.findByLongUrl(longUrl)
                .orElseGet(() -> {
                    String shortCode = generateUniqueShortCode(longUrl);
                    Url url = Url.builder()
                            .shortCode(shortCode)
                            .longUrl(longUrl)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return urlRepository.save(url);
                });
    }

    public String getOriginalUrl(String shortCode) {
        return urlRepository.findByShortCode(shortCode)
                .map(Url::getLongUrl)
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
