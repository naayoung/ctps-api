package com.ctps.ctps_api.global.security;

import com.ctps.ctps_api.global.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
@RequiredArgsConstructor
public class CsrfProtectionInterceptor implements HandlerInterceptor {

    private final OriginVerifier originVerifier;
    private final CsrfCookieManager csrfCookieManager;

    @Value("${auth.session.ttl-days:14}")
    private long sessionTtlDays;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isSafeMethod(request.getMethod())) {
            String csrfToken = csrfCookieManager.ensureToken(request, response, Duration.ofDays(sessionTtlDays));
            response.setHeader(CsrfCookieManager.CSRF_HEADER_NAME, csrfToken);
            return true;
        }

        if (!originVerifier.isAllowed(request)) {
            log.warn(
                    "csrf_origin_rejected requestId={} method={} uri={} origin={} referer={}",
                    request.getAttribute(RequestIdFilter.REQUEST_ID_HEADER),
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getHeader("Origin"),
                    request.getHeader("Referer")
            );
            throw new ForbiddenException("허용되지 않은 요청 출처입니다.");
        }

        String cookieToken = csrfCookieManager.extractToken(request);
        String headerToken = request.getHeader(CsrfCookieManager.CSRF_HEADER_NAME);
        if (!StringUtils.hasText(cookieToken) || !cookieToken.equals(headerToken)) {
            log.warn(
                    "csrf_token_rejected requestId={} method={} uri={}",
                    request.getAttribute(RequestIdFilter.REQUEST_ID_HEADER),
                    request.getMethod(),
                    request.getRequestURI()
            );
            throw new ForbiddenException("CSRF 검증에 실패했습니다.");
        }

        return true;
    }

    private boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }
}
