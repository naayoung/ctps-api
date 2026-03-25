package com.ctps.ctps_api.domain.auth.service;

import com.ctps.ctps_api.domain.auth.dto.AuthResponse;
import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.auth.entity.UserSession;
import com.ctps.ctps_api.domain.auth.repository.UserRepository;
import com.ctps.ctps_api.domain.auth.repository.UserSessionRepository;
import com.ctps.ctps_api.global.exception.UnauthorizedException;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import com.ctps.ctps_api.global.security.SessionCookieManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionCookieManager sessionCookieManager;

    @Value("${auth.session.ttl-days:14}")
    private long sessionTtlDays;

    @Transactional
    public AuthResponse login(String username, String password, HttpServletResponse response) {
        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        UserSession session = createSession(user);
        sessionCookieManager.setSessionCookie(response, session.getSessionToken(), sessionTtl());
        return AuthResponse.from(user);
    }

    public AuthResponse me() {
        Long userId = CurrentUserContext.getRequired().getId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("현재 사용자 정보를 찾을 수 없습니다."));
        return AuthResponse.from(user);
    }

    @Transactional
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        UserSession session = getActiveSession(request);
        LocalDateTime now = LocalDateTime.now();
        session.refresh(now, now.plus(sessionTtl()));
        sessionCookieManager.setSessionCookie(response, session.getSessionToken(), sessionTtl());
        return AuthResponse.from(session.getUser());
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionToken = sessionCookieManager.extractSessionToken(request);
        if (sessionToken != null && !sessionToken.isBlank()) {
            userSessionRepository.deleteBySessionToken(sessionToken);
        }
        sessionCookieManager.clearSessionCookie(response);
    }

    private UserSession getActiveSession(HttpServletRequest request) {
        String sessionToken = sessionCookieManager.extractSessionToken(request);
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new UnauthorizedException("세션이 존재하지 않습니다.");
        }

        return userSessionRepository.findBySessionTokenAndExpiresAtAfter(sessionToken, LocalDateTime.now())
                .orElseThrow(() -> new UnauthorizedException("세션이 만료되었습니다."));
    }

    private UserSession createSession(User user) {
        LocalDateTime now = LocalDateTime.now();
        return userSessionRepository.save(UserSession.builder()
                .user(user)
                .sessionToken(UUID.randomUUID().toString())
                .createdAt(now)
                .lastAccessedAt(now)
                .expiresAt(now.plus(sessionTtl()))
                .build());
    }

    private Duration sessionTtl() {
        return Duration.ofDays(sessionTtlDays);
    }
}
