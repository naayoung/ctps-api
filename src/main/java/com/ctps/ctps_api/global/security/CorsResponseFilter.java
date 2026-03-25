package com.ctps.ctps_api.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsResponseFilter extends OncePerRequestFilter {

    private final CorsOriginProperties corsOriginProperties;
    private final long maxAgeSeconds;

    public CorsResponseFilter(
            CorsOriginProperties corsOriginProperties,
            @Value("${app.cors.max-age-seconds:3600}") long maxAgeSeconds
    ) {
        this.corsOriginProperties = corsOriginProperties;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = corsOriginProperties.normalizeRequestOrigin(request.getHeader("Origin"));

        if (StringUtils.hasText(origin) && corsOriginProperties.isAllowedOrigin(origin)) {
            response.addHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
            response.setHeader(
                    "Access-Control-Allow-Headers",
                    resolveAllowedHeaders(request)
            );
            response.setHeader(
                    "Access-Control-Expose-Headers",
                    RequestIdFilter.REQUEST_ID_HEADER + ", " + CsrfCookieManager.CSRF_HEADER_NAME
            );
            response.setHeader("Access-Control-Max-Age", String.valueOf(maxAgeSeconds));
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveAllowedHeaders(HttpServletRequest request) {
        String requestedHeaders = request.getHeader("Access-Control-Request-Headers");
        if (StringUtils.hasText(requestedHeaders)) {
            return requestedHeaders;
        }
        return "Content-Type, X-CSRF-Token, X-Requested-With";
    }
}
