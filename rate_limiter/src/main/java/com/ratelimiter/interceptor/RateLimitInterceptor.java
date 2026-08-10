package com.ratelimiter.interceptor;

import com.ratelimiter.exception.RateLimitExceededException;
import com.ratelimiter.ratelimit.ClientIdentifier;
import com.ratelimiter.ratelimit.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ClientIdentifier clientIdentifier;
    private final RateLimiterService rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientId = clientIdentifier.resolve(request);
        if (!rateLimiter.tryAcquire(clientId)) {
            throw new RateLimitExceededException(clientId, rateLimiter.retryAfterSeconds(clientId));
        }
        return true;
    }
}