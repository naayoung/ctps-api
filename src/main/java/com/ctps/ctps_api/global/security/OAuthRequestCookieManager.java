package com.ctps.ctps_api.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OAuthRequestCookieManager {

    private static final String STATE_COOKIE_NAME = "CTPS_OAUTH_STATE";
    private static final String REDIRECT_COOKIE_NAME = "CTPS_OAUTH_REDIRECT";

    private final CookiePolicy cookiePolicy;
    private final String cookieDomain;

    public OAuthRequestCookieManager(CookiePolicyResolver cookiePolicyResolver) {
        this.cookiePolicy = cookiePolicyResolver.getPolicy();
        this.cookieDomain = cookiePolicyResolver.getCookieDomain();
    }

    public void setState(HttpServletResponse response, String value, Duration ttl) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(STATE_COOKIE_NAME, value, ttl).toString());
    }

    public void setRedirectTarget(HttpServletResponse response, String value, Duration ttl) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(REDIRECT_COOKIE_NAME, value, ttl).toString());
    }

    public String extractState(HttpServletRequest request) {
        return extractCookie(request, STATE_COOKIE_NAME);
    }

    public String extractRedirectTarget(HttpServletRequest request) {
        return extractCookie(request, REDIRECT_COOKIE_NAME);
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(STATE_COOKIE_NAME, "", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(REDIRECT_COOKIE_NAME, "", Duration.ZERO).toString());
    }

    private String extractCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return null;
        }

        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie buildCookie(String name, String value, Duration ttl) {
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(name, value)
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
