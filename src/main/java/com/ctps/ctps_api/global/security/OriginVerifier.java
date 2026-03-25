package com.ctps.ctps_api.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OriginVerifier {

    private final List<String> allowedOrigins;

    public OriginVerifier(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins
    ) {
        this.allowedOrigins = List.of(allowedOrigins.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    public boolean isAllowed(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (StringUtils.hasText(origin)) {
            return allowedOrigins.contains(origin.trim());
        }

        String referer = request.getHeader("Referer");
        if (!StringUtils.hasText(referer)) {
            return false;
        }

        try {
            URI uri = URI.create(referer);
            String normalized = uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            return allowedOrigins.contains(normalized);
        } catch (Exception ignored) {
            return false;
        }
    }
}
