package com.ctps.ctps_api.domain.auth.controller;

import com.ctps.ctps_api.domain.auth.dto.AccountDeleteRequest;
import com.ctps.ctps_api.domain.auth.dto.AuthRequest;
import com.ctps.ctps_api.domain.auth.dto.AuthResponse;
import com.ctps.ctps_api.domain.auth.dto.FindUsernameRequest;
import com.ctps.ctps_api.domain.auth.dto.FindUsernameResponse;
import com.ctps.ctps_api.domain.auth.dto.PasswordChangeRequest;
import com.ctps.ctps_api.domain.auth.dto.PasswordResetConfirmRequest;
import com.ctps.ctps_api.domain.auth.dto.PasswordResetRequest;
import com.ctps.ctps_api.domain.auth.dto.PasswordResetRequestResponse;
import com.ctps.ctps_api.domain.auth.dto.SignUpRequest;
import com.ctps.ctps_api.domain.auth.entity.OAuthProvider;
import com.ctps.ctps_api.domain.auth.service.AuthService;
import com.ctps.ctps_api.domain.auth.service.OAuthAuthenticationException;
import com.ctps.ctps_api.domain.auth.service.OAuthService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuthService oAuthService;
    private final InMemoryRateLimitService rateLimitService;
    private final ClientRequestResolver clientRequestResolver;

    @Value("${security.rate-limit.login.max-attempts:10}")
    private int loginMaxAttempts;

    @Value("${security.rate-limit.login.window-seconds:300}")
    private long loginWindowSeconds;

    @Value("${security.rate-limit.username-recovery.max-attempts:5}")
    private int usernameRecoveryMaxAttempts;

    @Value("${security.rate-limit.username-recovery.window-seconds:600}")
    private long usernameRecoveryWindowSeconds;

    @Value("${security.rate-limit.password-reset-request.max-attempts:5}")
    private int passwordResetRequestMaxAttempts;

    @Value("${security.rate-limit.password-reset-request.window-seconds:600}")
    private long passwordResetRequestWindowSeconds;

    @Value("${security.rate-limit.password-reset-confirm.max-attempts:10}")
    private int passwordResetConfirmMaxAttempts;

    @Value("${security.rate-limit.password-reset-confirm.window-seconds:600}")
    private long passwordResetConfirmWindowSeconds;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        String clientKey = clientRequestResolver.resolveClientKey(httpServletRequest);
        String rateLimitKey = "login:" + clientKey + ":" + request.getEmail().trim().toLowerCase();
        rateLimitService.check(
                rateLimitKey,
                loginMaxAttempts,
                Duration.ofSeconds(loginWindowSeconds),
                "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."
        );
        AuthResponse authResponse = authService.login(request.getEmail(), request.getPassword(), response);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", authResponse));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @Valid @RequestBody SignUpRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.register(request, response);
        return ResponseEntity.ok(ApiResponse.success("회원가입이 완료되었습니다.", authResponse));
    }

    @PostMapping("/username/recovery")
    public ResponseEntity<ApiResponse<FindUsernameResponse>> recoverUsername(
            @Valid @RequestBody FindUsernameRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String clientKey = clientRequestResolver.resolveClientKey(httpServletRequest);
        String rateLimitKey = "username-recovery:" + clientKey + ":" + request.getEmail().trim().toLowerCase();
        rateLimitService.check(
                rateLimitKey,
                usernameRecoveryMaxAttempts,
                Duration.ofSeconds(usernameRecoveryWindowSeconds),
                "아이디 찾기 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."
        );
        FindUsernameResponse result = authService.findUsername(request);
        String message = switch (result.getStatus()) {
            case "FOUND" -> "입력한 정보와 일치하는 아이디를 확인했어요.";
            case "SOCIAL_ACCOUNT" -> "이 계정은 소셜 로그인 계정입니다. 해당 서비스에서 로그인해 주세요.";
            default -> "입력한 정보와 일치하는 일반 계정을 찾지 못했습니다.";
        };
        return ResponseEntity.ok(ApiResponse.success(message, result));
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

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        AuthResponse authResponse = authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success("비밀번호가 변경되었습니다.", authResponse));
    }

    @PostMapping("/password/reset/request")
    public ResponseEntity<ApiResponse<PasswordResetRequestResponse>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String clientKey = clientRequestResolver.resolveClientKey(httpServletRequest);
        String rateLimitKey = "password-reset-request:" + clientKey + ":" + request.getEmail().trim().toLowerCase();
        rateLimitService.check(
                rateLimitKey,
                passwordResetRequestMaxAttempts,
                Duration.ofSeconds(passwordResetRequestWindowSeconds),
                "비밀번호 재설정 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
        );
        PasswordResetRequestResponse result = authService.requestPasswordReset(request, clientKey);
        String message = switch (result.getStatus()) {
            case "TOKEN_ISSUED" -> "입력한 정보가 일반 계정과 일치하면 비밀번호 재설정 안내를 진행합니다.";
            case "SOCIAL_ACCOUNT" -> "이 계정은 소셜 로그인 계정입니다. 해당 서비스에서 로그인해 주세요.";
            default -> "입력한 정보가 일반 계정과 일치하면 비밀번호 재설정 안내를 진행합니다.";
        };
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @PostMapping("/password/reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String clientKey = clientRequestResolver.resolveClientKey(httpServletRequest);
        String rateLimitKey = "password-reset-confirm:" + clientKey;
        rateLimitService.check(
                rateLimitKey,
                passwordResetConfirmMaxAttempts,
                Duration.ofSeconds(passwordResetConfirmWindowSeconds),
                "비밀번호 재설정 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."
        );
        authService.confirmPasswordReset(request);
        return ResponseEntity.ok(ApiResponse.success("비밀번호가 재설정되었습니다. 새 비밀번호로 로그인해 주세요."));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @RequestBody AccountDeleteRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        authService.withdraw(request, httpServletRequest, response);
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴가 완료되었습니다."));
    }

    @GetMapping("/oauth/{provider}/start")
    public ResponseEntity<Void> startOAuthLogin(
            @PathVariable String provider,
            @RequestParam(required = false) String redirect,
            HttpServletResponse response
    ) {
        String redirectUrl;
        try {
            redirectUrl = oAuthService.prepareAuthorizationRedirect(OAuthProvider.from(provider), redirect, response);
        } catch (IllegalArgumentException | OAuthAuthenticationException exception) {
            redirectUrl = oAuthService.buildFrontendRedirect("error", redirect, exception.getMessage());
        }

        return ResponseEntity.status(302)
                .header("Location", redirectUrl)
                .build();
    }

    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> handleOAuthCallback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String redirectUrl;
        String requestedRedirect = oAuthService.extractRedirectTarget(request);

        try {
            OAuthProvider resolvedProvider = OAuthProvider.from(provider);

            if (error != null) {
                String status = "access_denied".equalsIgnoreCase(error) ? "cancelled" : "error";
                String message = errorDescription != null ? errorDescription : "소셜 로그인 인증이 완료되지 않았습니다.";
                redirectUrl = oAuthService.buildFrontendRedirect(status, requestedRedirect, message);
            } else if (code == null || code.isBlank()) {
                redirectUrl = oAuthService.buildFrontendRedirect("error", requestedRedirect, "OAuth 인증 코드가 전달되지 않았습니다.");
            } else {
                redirectUrl = oAuthService.handleCallbackSuccess(resolvedProvider, code, state, request, response);
            }
        } catch (IllegalArgumentException | OAuthAuthenticationException exception) {
            redirectUrl = oAuthService.buildFrontendRedirect("error", requestedRedirect, exception.getMessage());
        }

        return ResponseEntity.status(302)
                .header("Location", redirectUrl)
                .build();
    }
}
