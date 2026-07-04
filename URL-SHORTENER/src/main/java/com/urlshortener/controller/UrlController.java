package com.urlshortener.controller;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.exception.ShortCodeNotFoundException;
import com.urlshortener.model.Url;
import com.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    @RequestMapping(method = RequestMethod.HEAD, path = "/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shortenUrl(@Valid @RequestBody ShortenRequest request) {
        Url url = urlService.createShortUrl(request.getLongUrl());
        String shortUrl = "http://localhost:8080/" + url.getShortCode();
        return ResponseEntity.ok(new ShortenResponse(shortUrl));
    }

    @GetMapping("/{shortCode:[a-f0-9]{7}}")
    public ResponseEntity<Void> redirectToLongUrl(@PathVariable String shortCode) {
        String longUrl = urlService.getOriginalUrl(shortCode);
        if (longUrl == null) {
            throw new ShortCodeNotFoundException(shortCode);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }

}
