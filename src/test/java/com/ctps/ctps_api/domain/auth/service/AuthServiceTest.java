package com.ctps.ctps_api.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ctps.ctps_api.domain.auth.dto.AuthResponse;
import com.ctps.ctps_api.domain.auth.dto.PasswordResetRequest;
import com.ctps.ctps_api.domain.auth.dto.SignUpRequest;
import com.ctps.ctps_api.domain.auth.entity.AuthProvider;
import com.ctps.ctps_api.domain.auth.entity.PasswordResetToken;
import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.auth.entity.UserSession;
import com.ctps.ctps_api.domain.auth.repository.OAuthAccountRepository;
import com.ctps.ctps_api.domain.auth.repository.PasswordResetTokenRepository;
import com.ctps.ctps_api.domain.auth.repository.UserRepository;
import com.ctps.ctps_api.domain.auth.repository.UserSessionRepository;
import com.ctps.ctps_api.global.exception.ConflictException;
import com.ctps.ctps_api.global.security.CsrfCookieManager;
import com.ctps.ctps_api.global.security.SessionCookieManager;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final OAuthAccountRepository oAuthAccountRepository = Mockito.mock(OAuthAccountRepository.class);
    private final PasswordResetTokenRepository passwordResetTokenRepository = Mockito.mock(PasswordResetTokenRepository.class);
    private final UserSessionRepository userSessionRepository = Mockito.mock(UserSessionRepository.class);
    private final PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
    private final PasswordResetNotifier passwordResetNotifier = Mockito.mock(PasswordResetNotifier.class);
    private final SessionCookieManager sessionCookieManager = Mockito.mock(SessionCookieManager.class);
    private final CsrfCookieManager csrfCookieManager = Mockito.mock(CsrfCookieManager.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final AuthService authService = new AuthService(
            userRepository,
            oAuthAccountRepository,
            passwordResetTokenRepository,
            userSessionRepository,
            passwordEncoder,
            passwordResetNotifier,
            sessionCookieManager,
            csrfCookieManager,
            clock
    );

    @BeforeEach
    void setUp() throws Exception {
        setField(authService, "sessionTtlDays", 14L);
        setField(authService, "passwordResetTokenTtlMinutes", 30L);
        setField(authService, "frontendBaseUrl", "http://localhost:5173");
        setField(authService, "passwordResetPagePath", "/?mode=reset-password");
        setField(authService, "exposePasswordResetTokenInResponse", false);

        given(userSessionRepository.save(any(UserSession.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(passwordEncoder.encode(anyString()))
                .willAnswer(invocation -> "encoded-" + invocation.getArgument(0));
    }

    @Test
    @DisplayName("회원가입은 메일 인증 없이 바로 성공하고 세션을 생성한다")
    void register_signsUpWithoutEmailVerification() throws Exception {
        SignUpRequest request = signUpRequest("user@example.com", "테스터", "Password1", "Password1");

        given(userRepository.findByEmailAndDeletedAtIsNull("user@example.com")).willReturn(Optional.empty());
        given(userRepository.findByUsername(anyString())).willReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthResponse authResponse = authService.register(request, response);

        assertThat(authResponse.getEmail()).isEqualTo("user@example.com");
        verify(userRepository).save(any(User.class));
        verify(userSessionRepository).save(any(UserSession.class));
        verify(sessionCookieManager).setSessionCookie(any(HttpServletResponse.class), anyString(), any());
        verify(csrfCookieManager).setToken(any(HttpServletResponse.class), anyString(), any());
    }

    @Test
    @DisplayName("로그인은 emailVerifiedAt 값과 무관하게 성공한다")
    void login_succeedsEvenWhenEmailVerifiedAtIsNull() {
        User user = User.builder()
                .username("local_user")
                .passwordHash("encoded-Password1")
                .displayName("테스터")
                .email("user@example.com")
                .primaryAuthProvider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now(clock))
                .updatedAt(LocalDateTime.now(clock))
                .emailVerifiedAt(null)
                .build();

        given(userRepository.findByEmailAndDeletedAtIsNull("user@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Password1", "encoded-Password1")).willReturn(true);
        given(userSessionRepository.save(any(UserSession.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        AuthResponse authResponse = authService.login("user@example.com", "Password1", new MockHttpServletResponse());

        assertThat(authResponse.getEmail()).isEqualTo("user@example.com");
        verify(userSessionRepository).save(any(UserSession.class));
    }

    @Test
    @DisplayName("이미 가입된 local 이메일은 중복 가입을 막고 기존 계정을 덮어쓰지 않는다")
    void register_rejectsDuplicateLocalEmail() throws Exception {
        User existing = User.builder()
                .username("existing_user")
                .passwordHash("encoded-old")
                .displayName("기존 사용자")
                .email("user@example.com")
                .primaryAuthProvider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now(clock))
                .updatedAt(LocalDateTime.now(clock))
                .build();
        SignUpRequest request = signUpRequest("user@example.com", "테스터", "Password1", "Password1");

        given(userRepository.findByEmailAndDeletedAtIsNull("user@example.com")).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.register(request, new MockHttpServletResponse()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 가입된 이메일입니다.");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 이메일 인증 여부와 무관하게 local 계정이면 진행한다")
    void requestPasswordReset_doesNotRequireEmailVerification() throws Exception {
        User user = User.builder()
                .username("local_user")
                .passwordHash("encoded-Password1")
                .displayName("테스터")
                .email("user@example.com")
                .primaryAuthProvider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now(clock))
                .updatedAt(LocalDateTime.now(clock))
                .build();
        PasswordResetToken savedToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash("hashed")
                .requestedByKey("client-key")
                .createdAt(LocalDateTime.now(clock))
                .expiresAt(LocalDateTime.now(clock).plusMinutes(30))
                .build();

        given(userRepository.findByEmailAndDeletedAtIsNull("user@example.com")).willReturn(Optional.of(user));
        given(passwordResetTokenRepository.save(any(PasswordResetToken.class))).willReturn(savedToken);

        var response = authService.requestPasswordReset(
                passwordResetRequest("user@example.com"),
                "client-key"
        );

        assertThat(response.getStatus()).isEqualTo("REQUEST_ACCEPTED");
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(passwordResetNotifier).send(any(User.class), anyString(), any(LocalDateTime.class));
    }

    private SignUpRequest signUpRequest(String email, String displayName, String password, String passwordConfirm) throws Exception {
        SignUpRequest request = new SignUpRequest();
        setField(request, "email", email);
        setField(request, "displayName", displayName);
        setField(request, "password", password);
        setField(request, "passwordConfirm", passwordConfirm);
        return request;
    }

    private PasswordResetRequest passwordResetRequest(String email) throws Exception {
        PasswordResetRequest request = new PasswordResetRequest();
        setField(request, "email", email);
        return request;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
