package com.ctps.ctps_api.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CsrfCookieManager {

    public static final String CSRF_COOKIE_NAME = "CTPS_CSRF";
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";

    private final CookiePolicy cookiePolicy;
    private final String cookieDomain;

    public CsrfCookieManager(CookiePolicyResolver cookiePolicyResolver) {
        this.cookiePolicy = cookiePolicyResolver.getPolicy();
        this.cookieDomain = cookiePolicyResolver.getCookieDomain();
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
                .secure(cookiePolicy.secure())
                .sameSite(cookiePolicy.sameSite())
                .path("/")
                .maxAge(ttl);

        if (StringUtils.hasText(cookieDomain)) {
            cookieBuilder.domain(cookieDomain);
        }

        return cookieBuilder.build();
    }
}
