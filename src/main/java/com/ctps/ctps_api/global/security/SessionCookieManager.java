package com.ctps.ctps_api.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieManager {

    public static final String SESSION_COOKIE_NAME = "CTPS_SESSION";

    private final CookiePolicy cookiePolicy;
    private final String cookieDomain;

    public SessionCookieManager(
            CookiePolicyResolver cookiePolicyResolver
    ) {
        this.cookiePolicy = cookiePolicyResolver.getPolicy();
        this.cookieDomain = cookiePolicyResolver.getCookieDomain();
    }

    public void setSessionCookie(HttpServletResponse response, String sessionToken, Duration ttl) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(sessionToken, ttl).toString());
    }

    public void clearSessionCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
    }

    public String extractSessionToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie buildCookie(String value, Duration ttl) {
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(SESSION_COOKIE_NAME, value)
                .httpOnly(true)
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
