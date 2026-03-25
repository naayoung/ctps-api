package com.ctps.ctps_api.domain.auth.service;

import com.ctps.ctps_api.domain.auth.dto.AccountDeleteRequest;
import com.ctps.ctps_api.domain.auth.dto.AuthResponse;
import com.ctps.ctps_api.domain.auth.dto.FindUsernameRequest;
import com.ctps.ctps_api.domain.auth.dto.FindUsernameResponse;
import com.ctps.ctps_api.domain.auth.dto.PasswordChangeRequest;
import com.ctps.ctps_api.domain.auth.dto.PasswordResetConfirmRequest;
import com.ctps.ctps_api.domain.auth.dto.PasswordResetRequest;
import com.ctps.ctps_api.domain.auth.dto.PasswordResetRequestResponse;
import com.ctps.ctps_api.domain.auth.dto.SignUpRequest;
import com.ctps.ctps_api.domain.auth.entity.AuthProvider;
import com.ctps.ctps_api.domain.auth.entity.OAuthAccount;
import com.ctps.ctps_api.domain.auth.entity.OAuthProvider;
import com.ctps.ctps_api.domain.auth.entity.PasswordResetToken;
import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.auth.entity.UserSession;
import com.ctps.ctps_api.domain.auth.repository.OAuthAccountRepository;
import com.ctps.ctps_api.domain.auth.repository.PasswordResetTokenRepository;
import com.ctps.ctps_api.domain.auth.repository.UserRepository;
import com.ctps.ctps_api.domain.auth.repository.UserSessionRepository;
import com.ctps.ctps_api.global.exception.BadRequestException;
import com.ctps.ctps_api.global.exception.ConflictException;
import com.ctps.ctps_api.global.exception.UnauthorizedException;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import com.ctps.ctps_api.global.security.CsrfCookieManager;
import com.ctps.ctps_api.global.security.SessionCookieManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String ACCOUNT_DELETE_CONFIRM_TEXT = "탈퇴합니다";
    private static final String RECOVERY_REQUEST_ACCEPTED = "REQUEST_ACCEPTED";
    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetNotifier passwordResetNotifier;
    private final SessionCookieManager sessionCookieManager;
    private final CsrfCookieManager csrfCookieManager;
    private final Clock clock;

    @Value("${auth.session.ttl-days:14}")
    private long sessionTtlDays;

    @Value("${auth.password-reset.token-ttl-minutes:30}")
    private long passwordResetTokenTtlMinutes;

    @Value("${auth.password-reset.expose-token-in-response:false}")
    private boolean exposePasswordResetTokenInResponse;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${auth.password-reset.reset-page-path:/?mode=reset-password}")
    private String passwordResetPagePath;

    @Transactional
    public AuthResponse register(SignUpRequest request, HttpServletResponse response) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        String displayName = normalizeDisplayName(request.getDisplayName());
        validatePasswordConfirmation(request.getPassword(), request.getPasswordConfirm());
        validatePasswordRules(request.getPassword());

        LocalDateTime now = now();
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .map(existing -> prepareExistingUserForSignup(existing, displayName, request.getPassword(), now))
                .orElseGet(() -> createLocalUser(normalizedEmail, displayName, request.getPassword(), now));
        createSessionAndCookies(user, response);
        return AuthResponse.from(user);
    }

    public FindUsernameResponse findUsername(FindUsernameRequest request) {
        return FindUsernameResponse.builder()
                .status(RECOVERY_REQUEST_ACCEPTED)
                .build();
    }

    @Transactional
    public AuthResponse login(String email, String password, HttpServletResponse response) {
        User user = findActiveUserForLogin(email);

        if (!user.canUsePasswordAuth()) {
            throw new UnauthorizedException("이 계정은 비밀번호 로그인 대신 소셜 로그인을 이용해 주세요.");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        createSessionAndCookies(user, response);
        return AuthResponse.from(user);
    }

    public AuthResponse me() {
        User user = getCurrentActiveUser();
        return AuthResponse.from(user);
    }

    @Transactional
    public AuthResponse loginWithOAuth(OAuthUserProfile profile, HttpServletResponse response) {
        User user = findOrCreateUser(profile);
        createSessionAndCookies(user, response);
        return AuthResponse.from(user);
    }

    @Transactional
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        UserSession session = getActiveSession(request);
        if (session.getUser().isDeleted()) {
            throw new UnauthorizedException("탈퇴한 계정입니다.");
        }

        LocalDateTime now = now();
        session.refresh(now, now.plus(sessionTtl()));
        sessionCookieManager.setSessionCookie(response, session.getSessionToken(), sessionTtl());
        String csrfToken = UUID.randomUUID().toString();
        csrfCookieManager.setToken(response, csrfToken, sessionTtl());
        response.setHeader(CsrfCookieManager.CSRF_HEADER_NAME, csrfToken);
        return AuthResponse.from(session.getUser());
    }

    @Transactional
    public AuthResponse changePassword(PasswordChangeRequest request) {
        User user = getCurrentActiveUser();
        String currentSessionToken = CurrentUserContext.getRequired().getSessionToken();

        if (!user.canUsePasswordAuth()) {
            throw new BadRequestException("소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.");
        }

        validatePasswordConfirmation(request.getNewPassword(), request.getNewPasswordConfirm());
        validatePasswordRules(request.getNewPassword());

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("현재 비밀번호가 올바르지 않습니다.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BadRequestException("새 비밀번호는 현재 비밀번호와 다르게 설정해 주세요.");
        }

        LocalDateTime now = now();
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()), now);
        userSessionRepository.deleteByUser_Id(user.getId());
        userSessionRepository.save(UserSession.builder()
                .user(user)
                .sessionToken(currentSessionToken)
                .createdAt(now)
                .lastAccessedAt(now)
                .expiresAt(now.plus(sessionTtl()))
                .build());
        return AuthResponse.from(user);
    }

    @Transactional
    public PasswordResetRequestResponse requestPasswordReset(PasswordResetRequest request, String requestedByKey) {
        User user = findUserForRecovery(request.getEmail());

        if (user == null || !user.canUsePasswordAuth()) {
            return PasswordResetRequestResponse.builder()
                    .status(RECOVERY_REQUEST_ACCEPTED)
                    .build();
        }

        LocalDateTime now = now();
        invalidateOutstandingResetTokens(user.getId(), now);

        String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        PasswordResetToken token = passwordResetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(hashToken(rawToken))
                .expiresAt(now.plusMinutes(passwordResetTokenTtlMinutes))
                .createdAt(now)
                .requestedByKey(limitRequestedByKey(requestedByKey))
                .build());

        String resetLink = buildPasswordResetLink(rawToken);
        passwordResetNotifier.send(user, resetLink, token.getExpiresAt());

        return PasswordResetRequestResponse.builder()
                .status(RECOVERY_REQUEST_ACCEPTED)
                .expiresInMinutes(passwordResetTokenTtlMinutes)
                .resetLink(exposePasswordResetTokenInResponse ? resetLink : null)
                .resetToken(exposePasswordResetTokenInResponse ? rawToken : null)
                .build();
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        validatePasswordConfirmation(request.getNewPassword(), request.getNewPasswordConfirm());
        validatePasswordRules(request.getNewPassword());

        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hashToken(request.getToken().trim()))
                .orElseThrow(() -> new BadRequestException("유효하지 않거나 만료된 비밀번호 재설정 토큰입니다."));

        LocalDateTime now = now();
        if (token.isUsed() || token.isExpired(now)) {
            throw new BadRequestException("유효하지 않거나 만료된 비밀번호 재설정 토큰입니다.");
        }

        User user = token.getUser();
        if (user.isDeleted() || !user.canUsePasswordAuth()) {
            token.markUsed(now);
            throw new BadRequestException("비밀번호를 재설정할 수 없는 계정입니다.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BadRequestException("새 비밀번호는 현재 비밀번호와 다르게 설정해 주세요.");
        }

        user.updatePassword(passwordEncoder.encode(request.getNewPassword()), now);
        token.markUsed(now);
        invalidateOutstandingResetTokens(user.getId(), now);
        userSessionRepository.deleteByUser_Id(user.getId());
    }

    @Transactional
    public void withdraw(AccountDeleteRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        User user = getCurrentActiveUser();

        String confirmationText = request.getConfirmationText() == null ? "" : request.getConfirmationText().trim();
        if (!ACCOUNT_DELETE_CONFIRM_TEXT.equals(confirmationText)) {
            throw new BadRequestException("\"탈퇴합니다\"를 정확히 입력해 주세요.");
        }

        if (user.canUsePasswordAuth()) {
            if (!StringUtils.hasText(request.getCurrentPassword())) {
                throw new BadRequestException("회원 탈퇴를 위해 현재 비밀번호를 입력해 주세요.");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                throw new UnauthorizedException("현재 비밀번호가 올바르지 않습니다.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        String anonymizedUsername = "deleted_user_" + user.getId() + "_" + now.toLocalDate();
        String anonymizedEmail = "deleted+" + user.getId() + "@ctps.local";

        oAuthAccountRepository.deleteByUser_Id(user.getId());
        userSessionRepository.deleteByUser_Id(user.getId());
        user.markDeleted(anonymizedUsername, anonymizedEmail, now);

        logout(httpRequest, response);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionToken = sessionCookieManager.extractSessionToken(request);
        if (sessionToken != null && !sessionToken.isBlank()) {
            userSessionRepository.deleteBySessionToken(sessionToken);
        }
        sessionCookieManager.clearSessionCookie(response);
        csrfCookieManager.clearToken(response);
        response.setHeader(CsrfCookieManager.CSRF_HEADER_NAME, "");
    }

    private User getCurrentActiveUser() {
        Long userId = CurrentUserContext.getRequired().getId();
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new UnauthorizedException("현재 사용자 정보를 찾을 수 없습니다."));
    }

    private User findActiveUserForLogin(String email) {
        String normalizedEmail = normalizeEmail(email);

        return userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .or(() -> userRepository.findByUsernameAndDeletedAtIsNull(normalizedEmail))
                .orElseThrow(() -> new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    private User findUserForRecovery(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(normalizeEmail(email))
                .orElse(null);
    }

    private User prepareExistingUserForSignup(User existing, String displayName, String rawPassword, LocalDateTime now) {
        if (!existing.canUsePasswordAuth()) {
            throw new ConflictException("이미 소셜 로그인으로 가입된 이메일입니다. 해당 소셜 로그인을 이용해 주세요.");
        }
        throw new ConflictException("이미 가입된 이메일입니다.");
    }

    private User createLocalUser(String normalizedEmail, String displayName, String rawPassword, LocalDateTime now) {
        return userRepository.save(User.builder()
                .username(generateUniqueUsername(AuthProvider.LOCAL.name().toLowerCase(Locale.ROOT), normalizedEmail))
                .passwordHash(passwordEncoder.encode(rawPassword))
                .displayName(displayName)
                .email(normalizedEmail)
                .primaryAuthProvider(AuthProvider.LOCAL)
                .createdAt(now)
                .updatedAt(now)
                .emailVerifiedAt(now)
                .build());
    }

    private UserSession getActiveSession(HttpServletRequest request) {
        String sessionToken = sessionCookieManager.extractSessionToken(request);
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new UnauthorizedException("세션이 존재하지 않습니다.");
        }

        return userSessionRepository.findBySessionTokenAndExpiresAtAfter(sessionToken, LocalDateTime.now())
                .orElseThrow(() -> new UnauthorizedException("세션이 만료되었습니다."));
    }

    private void createSessionAndCookies(User user, HttpServletResponse response) {
        UserSession session = createSession(user);
        sessionCookieManager.setSessionCookie(response, session.getSessionToken(), sessionTtl());
        String csrfToken = UUID.randomUUID().toString();
        csrfCookieManager.setToken(response, csrfToken, sessionTtl());
        response.setHeader(CsrfCookieManager.CSRF_HEADER_NAME, csrfToken);
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

    private User findOrCreateUser(OAuthUserProfile profile) {
        LocalDateTime now = LocalDateTime.now();

        OAuthAccount account = oAuthAccountRepository
                .findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .map(existing -> {
                    existing.updateProfile(profile.email(), profile.nickname(), profile.profileImageUrl(), now);
                    synchronizeSocialUser(existing.getUser(), profile, now);
                    return existing;
                })
                .orElseGet(() -> createOAuthAccount(profile, now));

        return account.getUser();
    }

    private OAuthAccount createOAuthAccount(OAuthUserProfile profile, LocalDateTime now) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizeEmail(profile.email()))
                .map(existing -> {
                    synchronizeSocialUser(existing, profile, now);
                    return existing;
                })
                .orElseGet(() -> createOAuthUser(profile, now));

        return oAuthAccountRepository.save(OAuthAccount.builder()
                .user(user)
                .provider(profile.provider())
                .providerUserId(profile.providerUserId())
                .email(normalizeEmail(profile.email()))
                .nickname(profile.nickname())
                .profileImageUrl(profile.profileImageUrl())
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private void synchronizeSocialUser(User user, OAuthUserProfile profile, LocalDateTime now) {
        if (user.getPrimaryAuthProvider() == null) {
            user.syncPrimaryAuth(AuthProvider.fromOAuthProvider(profile.provider()), profile.providerUserId(), now);
        }

        if (user.getPrimaryAuthProvider() == AuthProvider.LOCAL) {
            user.updateProfile(
                    user.getDisplayName(),
                    normalizeEmail(profile.email()),
                    user.getProfileImageUrl() == null ? profile.profileImageUrl() : user.getProfileImageUrl(),
                    now
            );
            return;
        }

        user.syncPrimaryAuth(AuthProvider.fromOAuthProvider(profile.provider()), profile.providerUserId(), now);
        user.updateProfile(profile.nickname(), normalizeEmail(profile.email()), profile.profileImageUrl(), now);
        user.markEmailVerified(now);
    }

    private User createOAuthUser(OAuthUserProfile profile, LocalDateTime now) {
        String generatedUsername = generateUniqueUsername(
                profile.provider().name().toLowerCase(Locale.ROOT),
                normalizeEmail(profile.email())
        );
        String displayName = StringUtils.hasText(profile.nickname())
                ? profile.nickname().trim()
                : profile.provider().name().toLowerCase(Locale.ROOT) + " 사용자";

        return userRepository.save(User.builder()
                .username(generatedUsername)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .displayName(displayName)
                .email(normalizeEmail(profile.email()))
                .profileImageUrl(profile.profileImageUrl())
                .primaryAuthProvider(AuthProvider.fromOAuthProvider(profile.provider()))
                .primaryProviderUserId(profile.providerUserId())
                .createdAt(now)
                .updatedAt(now)
                .emailVerifiedAt(now)
                .build());
    }

    private String generateUniqueUsername(String prefix, String seedSource) {
        String seed = seedSource == null ? "" : seedSource.replaceAll("[^a-zA-Z0-9]", "");
        if (!StringUtils.hasText(seed)) {
            seed = prefix + "user";
        }

        String base = prefix + "_" + seed.toLowerCase(Locale.ROOT);
        if (base.length() > 84) {
            base = base.substring(0, 84);
        }

        String candidate = base;
        int suffix = 1;
        while (userRepository.findByUsername(candidate).isPresent()) {
            String postfix = "_" + suffix++;
            int maxBaseLength = Math.max(1, 100 - postfix.length());
            candidate = base.substring(0, Math.min(base.length(), maxBaseLength)) + postfix;
        }
        return candidate;
    }

    private void validatePasswordConfirmation(String password, String passwordConfirm) {
        if (!StringUtils.hasText(passwordConfirm) || !password.equals(passwordConfirm)) {
            throw new BadRequestException("비밀번호 확인이 일치하지 않습니다.");
        }
    }

    private void validatePasswordRules(String password) {
        if (!StringUtils.hasText(password) || password.length() < 8) {
            throw new BadRequestException("비밀번호는 8자 이상으로 입력해 주세요.");
        }

        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BadRequestException("비밀번호는 영문과 숫자를 모두 포함해 주세요.");
        }
    }

    private void invalidateOutstandingResetTokens(Long userId, LocalDateTime now) {
        passwordResetTokenRepository.findAllByUser_IdAndUsedAtIsNull(userId)
                .forEach(existing -> existing.markUsed(now));
    }

    private String buildPasswordResetLink(String rawToken) {
        String normalizedBase = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;

        String normalizedPath = passwordResetPagePath.startsWith("/") ? passwordResetPagePath : "/" + passwordResetPagePath;
        String separator = normalizedPath.contains("?") ? "&" : "?";
        return normalizedBase + normalizedPath + separator + "token=" + rawToken;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
        }
    }

    private String limitRequestedByKey(String requestedByKey) {
        if (!StringUtils.hasText(requestedByKey)) {
            return "unknown";
        }
        String trimmed = requestedByKey.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BadRequestException("이메일을 입력해 주세요.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String displayName) {
        if (!StringUtils.hasText(displayName)) {
            throw new BadRequestException("표시 이름을 입력해 주세요.");
        }
        return displayName.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private Duration sessionTtl() {
        return Duration.ofDays(sessionTtlDays);
    }
}
