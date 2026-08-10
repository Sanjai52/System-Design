package com.ratelimiter.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Getter
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String clientId, long retryAfterSeconds) {
        super("Rate limit exceeded for client: " + clientId);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus status() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}