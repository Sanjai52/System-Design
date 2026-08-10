package com.ratelimiter.controller;

import com.ratelimiter.ratelimit.ClientIdentifier;
import com.ratelimiter.ratelimit.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DemoController {

    private final ClientIdentifier clientIdentifier;
    private final RateLimiterService rateLimiter;

    @GetMapping("/demo")
    public Map<String, Object> demo(HttpServletRequest request) {
        String clientId = clientIdentifier.resolve(request);
        return Map.of(
                "clientId", clientId,
                "message", "Request allowed by token bucket",
                "timestamp", Instant.now().toString());
    }

    @GetMapping("/ratelimit/status")
    public Map<String, Object> status(HttpServletRequest request) {
        return rateLimiter.status(clientIdentifier.resolve(request));
    }
}