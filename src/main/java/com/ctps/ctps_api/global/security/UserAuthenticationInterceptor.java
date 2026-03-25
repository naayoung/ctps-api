package com.ctps.ctps_api.global.security;

import com.ctps.ctps_api.domain.auth.entity.UserSession;
import com.ctps.ctps_api.domain.auth.repository.UserSessionRepository;
import com.ctps.ctps_api.global.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class UserAuthenticationInterceptor implements HandlerInterceptor {

    private final UserSessionRepository userSessionRepository;
    private final SessionCookieManager sessionCookieManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String sessionToken = sessionCookieManager.extractSessionToken(request);
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }

        UserSession session = userSessionRepository.findBySessionTokenAndExpiresAtAfter(sessionToken, LocalDateTime.now())
                .orElseThrow(() -> new UnauthorizedException("로그인 세션이 만료되었습니다."));

        CurrentUserContext.set(AuthenticatedUser.from(session.getUser()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserContext.clear();
    }
}
