package com.ctps.ctps_api.domain.auth.controller;

import com.ctps.ctps_api.domain.auth.dto.AuthRequest;
import com.ctps.ctps_api.domain.auth.dto.AuthResponse;
import com.ctps.ctps_api.domain.auth.service.AuthService;
import com.ctps.ctps_api.global.response.ApiResponse;
import com.ctps.ctps_api.global.security.ClientRequestResolver;
import com.ctps.ctps_api.global.security.InMemoryRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final InMemoryRateLimitService rateLimitService;
    private final ClientRequestResolver clientRequestResolver;

    @Value("${security.rate-limit.login.max-attempts:10}")
    private int loginMaxAttempts;

    @Value("${security.rate-limit.login.window-seconds:300}")
    private long loginWindowSeconds;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        String clientKey = clientRequestResolver.resolveClientKey(httpServletRequest);
        String rateLimitKey = "login:" + clientKey + ":" + request.getUsername().trim().toLowerCase();
        rateLimitService.check(
                rateLimitKey,
                loginMaxAttempts,
                Duration.ofSeconds(loginWindowSeconds),
                "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."
        );
        AuthResponse authResponse = authService.login(request.getUsername(), request.getPassword(), response);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", authResponse));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> me() {
        return ResponseEntity.ok(ApiResponse.success("현재 사용자 조회 성공", authService.me()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.refresh(request, response);
        return ResponseEntity.ok(ApiResponse.success("세션 갱신 성공", authResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(ApiResponse.success("로그아웃 성공"));
    }
}
