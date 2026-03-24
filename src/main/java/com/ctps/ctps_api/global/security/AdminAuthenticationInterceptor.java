package com.ctps.ctps_api.global.security;

import com.ctps.ctps_api.global.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class AdminAuthenticationInterceptor implements HandlerInterceptor {

    private final String adminToken;
    private final IpAllowlistMatcher ipAllowlistMatcher;
    private final boolean trustForwardedHeaders;

    public AdminAuthenticationInterceptor(
            @Value("${admin.security.token:}") String adminToken,
            @Value("${admin.security.allowed-ips:127.0.0.1/32,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}") String allowedIps,
            @Value("${admin.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders
    ) {
        this.adminToken = adminToken;
        this.ipAllowlistMatcher = new IpAllowlistMatcher(allowedIps);
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!StringUtils.hasText(adminToken)) {
            throw new UnauthorizedException("관리자 토큰이 설정되지 않았습니다.");
        }

        String headerToken = request.getHeader("X-Admin-Token");
        if (!adminToken.equals(headerToken)) {
            throw new UnauthorizedException("관리자 인증에 실패했습니다.");
        }

        String clientIp = resolveClientIp(request);
        if (!ipAllowlistMatcher.matches(clientIp)) {
            log.warn("admin access denied for ip={} path={}", clientIp, request.getRequestURI());
            throw new UnauthorizedException("관리자 IP 접근이 허용되지 않았습니다.");
        }

        return true;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwardedFor)) {
                return forwardedFor.split(",")[0].trim();
            }

            String realIp = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return realIp.trim();
            }
        }

        return request.getRemoteAddr();
    }
}
