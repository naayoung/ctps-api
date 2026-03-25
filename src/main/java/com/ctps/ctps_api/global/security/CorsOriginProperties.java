package com.ctps.ctps_api.global.security;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class CorsOriginProperties {

    private final List<String> allowedOrigins;
    private final List<String> allowedOriginPatterns;

    public CorsOriginProperties(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,https://ctps-web.vercel.app}") String allowedOrigins,
            @Value("${app.cors.allowed-origin-patterns:https://*.vercel.app,https://*.railway.app}") String allowedOriginPatterns
    ) {
        this.allowedOrigins = List.of(allowedOrigins.split(",")).stream()
                .map(this::normalizeConfiguredOrigin)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        this.allowedOriginPatterns = List.of(allowedOriginPatterns.split(",")).stream()
                .map(this::normalizeConfiguredPattern)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        log.info("configured cors allowed origins={}", this.allowedOrigins);
        log.info("configured cors allowed origin patterns={}", this.allowedOriginPatterns);
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public String normalizeRequestOrigin(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return normalizeOrigin(value);
    }

    public boolean isAllowedOrigin(String value) {
        String normalized = normalizeRequestOrigin(value);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }

        if (allowedOrigins.contains(normalized)) {
            return true;
        }

        return allowedOriginPatterns.stream().anyMatch(pattern -> matchesPattern(normalized, pattern));
    }

    private String normalizeConfiguredOrigin(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim();
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        return normalizeOrigin(normalized);
    }

    private String normalizeConfiguredPattern(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim();
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        return normalized.replaceAll("/+$", "");
    }

    private String normalizeOrigin(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
                return value.trim().replaceAll("/+$", "");
            }

            return scheme + "://" + host + (port > 0 ? ":" + port : "");
        } catch (Exception exception) {
            return value.trim().replaceAll("/+$", "");
        }
    }

    private boolean matchesPattern(String origin, String pattern) {
        String regex = Pattern.quote(pattern).replace("\\*", ".*");
        return origin.matches("^" + regex + "$");
    }
}
