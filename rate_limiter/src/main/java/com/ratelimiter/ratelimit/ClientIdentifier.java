package com.ratelimiter.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIdentifier {

    public static final String CLIENT_ID_HEADER = "X-Client-Id";

    public String resolve(HttpServletRequest request) {
        String clientId = request.getHeader(CLIENT_ID_HEADER);
        if (clientId != null && !clientId.isBlank()) {
            return clientId.trim();
        }
        String ip = request.getRemoteAddr();
        return ip == null ? "unknown" : ip;
    }
}