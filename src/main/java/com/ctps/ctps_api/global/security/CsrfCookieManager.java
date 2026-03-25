package com.ctps.ctps_api.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class CsrfCookieManager {

    public static final String CSRF_COOKIE_NAME = "CTPS_CSRF";
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";

    private final boolean secureCookie;
    private final String sameSite;
    private final String cookieDomain;

    public CsrfCookieManager(
            @Value("${auth.session.secure-cookie:AUTO}") String secureCookie,
            @Value("${auth.session.same-site:AUTO}") String sameSite,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl,
            @Value("${app.deployment.mode:local}") String deploymentMode,
            @Value("${auth.session.cookie-domain:}") String cookieDomain
    ) {
        this.secureCookie = resolveSecureCookie(secureCookie, frontendBaseUrl, deploymentMode);
        this.sameSite = resolveSameSite(sameSite, this.secureCookie, frontendBaseUrl, deploymentMode);
        this.cookieDomain = cookieDomain;

        log.info(
                "csrf cookie policy secure={} sameSite={} domain={} deploymentMode={} frontendBaseUrl={}",
                this.secureCookie,
                this.sameSite,
                StringUtils.hasText(this.cookieDomain) ? this.cookieDomain : "(default)",
                deploymentMode,
                frontendBaseUrl
        );
    }

    public String ensureToken(HttpServletRequest request, HttpServletResponse response, Duration ttl) {
        String existing = extractToken(request);
        if (StringUtils.hasText(existing)) {
            return existing;
        }

        String generated = UUID.randomUUID().toString();
        setToken(response, generated, ttl);
        return generated;
    }

    public void setToken(HttpServletResponse response, String token, Duration ttl) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, ttl).toString());
    }

    public void clearToken(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
    }

    public String extractToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (CSRF_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private ResponseCookie buildCookie(String value, Duration ttl) {
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(CSRF_COOKIE_NAME, value)
                .httpOnly(false)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(ttl);

        if (StringUtils.hasText(cookieDomain)) {
            cookieBuilder.domain(cookieDomain);
        }

        return cookieBuilder.build();
    }

    private boolean resolveSecureCookie(String rawValue, String frontendBaseUrl, String deploymentMode) {
        if (StringUtils.hasText(rawValue) && !"AUTO".equalsIgnoreCase(rawValue.trim())) {
            return Boolean.parseBoolean(rawValue.trim());
        }

        return isCrossSiteProduction(frontendBaseUrl, deploymentMode);
    }

    private String resolveSameSite(String rawValue, boolean secure, String frontendBaseUrl, String deploymentMode) {
        if (StringUtils.hasText(rawValue) && !"AUTO".equalsIgnoreCase(rawValue.trim())) {
            String normalized = capitalize(rawValue.trim());
            if ("None".equalsIgnoreCase(normalized) && !secure) {
                log.warn("sameSite=None requires secure cookies; falling back to Lax");
                return "Lax";
            }
            return normalized;
        }

        return isCrossSiteProduction(frontendBaseUrl, deploymentMode) ? "None" : "Lax";
    }

    private boolean isCrossSiteProduction(String frontendBaseUrl, String deploymentMode) {
        String normalizedMode = deploymentMode == null ? "" : deploymentMode.trim().toLowerCase(Locale.ROOT);
        boolean localMode = normalizedMode.isBlank()
                || "local".equals(normalizedMode)
                || "dev".equals(normalizedMode)
                || "development".equals(normalizedMode);

        return !localMode && StringUtils.hasText(frontendBaseUrl) && frontendBaseUrl.startsWith("https://");
    }

    private String capitalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "Lax";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }
}
