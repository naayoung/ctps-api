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

    private final boolean secureCookie;
    private final String sameSite;
    private final String cookieDomain;

    public SessionCookieManager(
            @Value("${auth.session.secure-cookie:false}") boolean secureCookie,
            @Value("${auth.session.same-site:Lax}") String sameSite,
            @Value("${auth.session.cookie-domain:}") String cookieDomain
    ) {
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
        this.cookieDomain = cookieDomain;
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
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(ttl);

        if (StringUtils.hasText(cookieDomain)) {
            cookieBuilder.domain(cookieDomain);
        }

        return cookieBuilder.build();
    }
}
