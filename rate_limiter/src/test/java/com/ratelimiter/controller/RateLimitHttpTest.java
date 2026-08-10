package com.ratelimiter.controller;

import com.ratelimiter.config.WebConfig;
import com.ratelimiter.exception.GlobalExceptionHandler;
import com.ratelimiter.ratelimit.ClientIdentifier;
import com.ratelimiter.ratelimit.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
@Import({WebConfig.class, ClientIdentifier.class, GlobalExceptionHandler.class})
class RateLimitHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RateLimiterService rateLimiter;

    @Test
    void allowsRequestsWhileTokensAvailable() throws Exception {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/demo").header("X-Client-Id", "alice"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clientId").value("alice"));
        }
    }

    @Test
    void returns429WithRetryAfterWhenLimited() throws Exception {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(false);
        when(rateLimiter.retryAfterSeconds(anyString())).thenReturn(3L);
        mockMvc.perform(get("/api/demo").header("X-Client-Id", "alice"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3"))
                .andExpect(jsonPath("$.error").value("rate_limit_exceeded"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(3));
    }

    @Test
    void fallsBackToIpWhenNoClientIdHeader() throws Exception {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        mockMvc.perform(get("/api/demo").with(r -> {
            r.setRemoteAddr("10.1.2.3");
            return r;
        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("10.1.2.3"));
    }

    @Test
    void statusEndpointReportsBucket() throws Exception {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(rateLimiter.status(anyString())).thenReturn(Map.of(
                "clientId", "alice", "capacity", 10, "refillRate", 2.0, "tokens", 8.5));
        mockMvc.perform(get("/api/ratelimit/status").header("X-Client-Id", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokens").value(8.5));
    }
}