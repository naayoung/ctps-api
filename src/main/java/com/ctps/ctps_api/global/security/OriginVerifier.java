package com.ctps.ctps_api.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OriginVerifier {

    private final CorsOriginProperties corsOriginProperties;

    public OriginVerifier(CorsOriginProperties corsOriginProperties) {
        this.corsOriginProperties = corsOriginProperties;
    }

    public boolean isAllowed(HttpServletRequest request) {
        String origin = corsOriginProperties.normalizeRequestOrigin(request.getHeader("Origin"));
        if (StringUtils.hasText(origin)) {
            return corsOriginProperties.isAllowedOrigin(origin);
        }

        String referer = request.getHeader("Referer");
        if (!StringUtils.hasText(referer)) {
            return false;
        }

        try {
            URI uri = URI.create(referer);
            String normalized = uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            return corsOriginProperties.isAllowedOrigin(normalized);
        } catch (Exception ignored) {
            return false;
        }
    }
}
