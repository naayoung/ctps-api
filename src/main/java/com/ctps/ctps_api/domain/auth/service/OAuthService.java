package com.ctps.ctps_api.domain.auth.service;

import com.ctps.ctps_api.domain.auth.entity.OAuthProvider;
import com.ctps.ctps_api.global.security.OAuthRequestCookieManager;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class OAuthService {

    private final AuthService authService;
    private final OAuthRequestCookieManager oAuthRequestCookieManager;
    private final RestClient restClient = RestClient.create();

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${auth.oauth.state.ttl-minutes:10}")
    private long oauthStateTtlMinutes;

    @Value("${auth.oauth.kakao.enabled:false}")
    private boolean kakaoEnabled;

    @Value("${auth.oauth.kakao.client-id:}")
    private String kakaoClientId;

    @Value("${auth.oauth.kakao.client-secret:}")
    private String kakaoClientSecret;

    @Value("${auth.oauth.kakao.redirect-uri:}")
    private String kakaoRedirectUri;

    @Value("${auth.oauth.google.enabled:false}")
    private boolean googleEnabled;

    @Value("${auth.oauth.google.client-id:}")
    private String googleClientId;

    @Value("${auth.oauth.google.client-secret:}")
    private String googleClientSecret;

    @Value("${auth.oauth.google.redirect-uri:}")
    private String googleRedirectUri;

    @Value("${auth.oauth.github.enabled:false}")
    private boolean githubEnabled;

    @Value("${auth.oauth.github.client-id:}")
    private String githubClientId;

    @Value("${auth.oauth.github.client-secret:}")
    private String githubClientSecret;

    @Value("${auth.oauth.github.redirect-uri:}")
    private String githubRedirectUri;

    public String prepareAuthorizationRedirect(
            OAuthProvider provider,
            String redirectTarget,
            HttpServletResponse response
    ) {
        ensureProviderEnabled(provider);

        String sanitizedRedirect = sanitizeRedirectTarget(redirectTarget);
        String state = UUID.randomUUID().toString();
        Duration ttl = Duration.ofMinutes(oauthStateTtlMinutes);
        oAuthRequestCookieManager.setState(response, state, ttl);
        oAuthRequestCookieManager.setRedirectTarget(response, sanitizedRedirect, ttl);

        return switch (provider) {
            case KAKAO -> buildKakaoAuthorizationUrl(state);
            case GOOGLE -> buildGoogleAuthorizationUrl(state);
            case GITHUB -> buildGithubAuthorizationUrl(state);
        };
    }

    public String handleCallbackSuccess(
            OAuthProvider provider,
            String code,
            String state,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            validateState(request, state);
            OAuthUserProfile profile = fetchUserProfile(provider, code);
            authService.loginWithOAuth(profile, response);
            return buildFrontendRedirect("success", oAuthRequestCookieManager.extractRedirectTarget(request), null);
        } finally {
            oAuthRequestCookieManager.clear(response);
        }
    }

    public String extractRedirectTarget(HttpServletRequest request) {
        return oAuthRequestCookieManager.extractRedirectTarget(request);
    }

    public String buildFrontendRedirect(String status, String redirectTarget, String message) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/")
                .queryParam("oauth", status)
                .queryParam("redirect", sanitizeRedirectTarget(redirectTarget));

        if (StringUtils.hasText(message)) {
            builder.queryParam("message", message);
        }

        return builder.build(true).toUriString();
    }

    private OAuthUserProfile fetchUserProfile(OAuthProvider provider, String code) {
        try {
            return switch (provider) {
                case KAKAO -> fetchKakaoUserProfile(code);
                case GOOGLE -> fetchGoogleUserProfile(code);
                case GITHUB -> fetchGithubUserProfile(code);
            };
        } catch (OAuthAuthenticationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new OAuthAuthenticationException(providerDisplayName(provider) + " 로그인 중 외부 인증 서버와 통신하지 못했습니다.");
        } catch (RuntimeException exception) {
            throw new OAuthAuthenticationException(providerDisplayName(provider) + " 로그인 처리 중 예기치 못한 오류가 발생했습니다.");
        }
    }

    private OAuthUserProfile fetchKakaoUserProfile(String code) {
        JsonNode token = exchangeToken(
                "https://kauth.kakao.com/oauth/token",
                formData(
                        "grant_type", "authorization_code",
                        "client_id", kakaoClientId,
                        "client_secret", kakaoClientSecret,
                        "redirect_uri", kakaoRedirectUri,
                        "code", code
                ),
                null
        );
        String accessToken = requireText(token, "access_token", "카카오 access token을 받지 못했습니다.");
        JsonNode user = restClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

        String email = text(user.path("kakao_account").path("email"));
        if (!StringUtils.hasText(email)) {
            throw new OAuthAuthenticationException("카카오 로그인에는 이메일 제공 동의가 필요합니다.");
        }

        return new OAuthUserProfile(
                OAuthProvider.KAKAO,
                requireText(user, "id", "카카오 사용자 식별값을 확인하지 못했습니다."),
                email,
                text(user.path("kakao_account").path("profile").path("nickname")),
                text(user.path("kakao_account").path("profile").path("profile_image_url"))
        );
    }

    private OAuthUserProfile fetchGoogleUserProfile(String code) {
        JsonNode token = exchangeToken(
                "https://oauth2.googleapis.com/token",
                formData(
                        "grant_type", "authorization_code",
                        "client_id", googleClientId,
                        "client_secret", googleClientSecret,
                        "redirect_uri", googleRedirectUri,
                        "code", code
                ),
                null
        );
        String accessToken = requireText(token, "access_token", "구글 access token을 받지 못했습니다.");
        JsonNode user = restClient.get()
                .uri("https://openidconnect.googleapis.com/v1/userinfo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

        String email = text(user.path("email"));
        if (!StringUtils.hasText(email)) {
            throw new OAuthAuthenticationException("구글 계정 이메일을 확인하지 못했습니다.");
        }

        return new OAuthUserProfile(
                OAuthProvider.GOOGLE,
                requireText(user, "sub", "구글 사용자 식별값을 확인하지 못했습니다."),
                email,
                text(user.path("name")),
                text(user.path("picture"))
        );
    }

    private OAuthUserProfile fetchGithubUserProfile(String code) {
        JsonNode token = exchangeToken(
                "https://github.com/login/oauth/access_token",
                formData(
                        "client_id", githubClientId,
                        "client_secret", githubClientSecret,
                        "redirect_uri", githubRedirectUri,
                        "code", code
                ),
                "application/json"
        );
        String accessToken = requireText(token, "access_token", "GitHub access token을 받지 못했습니다.");

        JsonNode user = restClient.get()
                .uri("https://api.github.com/user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .retrieve()
                .body(JsonNode.class);

        String email = text(user.path("email"));
        if (!StringUtils.hasText(email)) {
            JsonNode emails = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(JsonNode.class);
            email = extractGithubEmail(emails);
        }

        if (!StringUtils.hasText(email)) {
            throw new OAuthAuthenticationException("GitHub 계정 이메일을 확인하지 못했습니다.");
        }

        String nickname = text(user.path("name"));
        if (!StringUtils.hasText(nickname)) {
            nickname = text(user.path("login"));
        }

        return new OAuthUserProfile(
                OAuthProvider.GITHUB,
                requireText(user, "id", "GitHub 사용자 식별값을 확인하지 못했습니다."),
                email,
                nickname,
                text(user.path("avatar_url"))
        );
    }

    private JsonNode exchangeToken(String url, MultiValueMap<String, String> formData, String acceptHeader) {
        RestClient.RequestBodySpec request = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);

        if (StringUtils.hasText(acceptHeader)) {
            request.header(HttpHeaders.ACCEPT, acceptHeader);
        }

        return request
                .body(formData)
                .retrieve()
                .body(JsonNode.class);
    }

    private String buildKakaoAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", kakaoClientId)
                .queryParam("redirect_uri", kakaoRedirectUri)
                .queryParam("scope", "profile_nickname profile_image account_email")
                .queryParam("state", state)
                .build(true)
                .toUriString();
    }

    private String buildGoogleAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("response_type", "code")
                .queryParam("client_id", googleClientId)
                .queryParam("redirect_uri", googleRedirectUri)
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("prompt", "select_account")
                .build(true)
                .toUriString();
    }

    private String buildGithubAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
                .queryParam("client_id", githubClientId)
                .queryParam("redirect_uri", githubRedirectUri)
                .queryParam("scope", "user:email")
                .queryParam("state", state)
                .queryParam("allow_signup", "true")
                .build(true)
                .toUriString();
    }

    private void validateState(HttpServletRequest request, String state) {
        String expected = oAuthRequestCookieManager.extractState(request);
        if (!StringUtils.hasText(state) || !StringUtils.hasText(expected) || !expected.equals(state)) {
            throw new OAuthAuthenticationException("OAuth 인증 상태값이 올바르지 않습니다. 다시 시도해 주세요.");
        }
    }

    private void ensureProviderEnabled(OAuthProvider provider) {
        boolean enabled = switch (provider) {
            case KAKAO -> kakaoEnabled && StringUtils.hasText(kakaoClientId) && StringUtils.hasText(kakaoRedirectUri);
            case GOOGLE -> googleEnabled && StringUtils.hasText(googleClientId) && StringUtils.hasText(googleRedirectUri);
            case GITHUB -> githubEnabled && StringUtils.hasText(githubClientId) && StringUtils.hasText(githubRedirectUri);
        };

        if (!enabled) {
            throw new OAuthAuthenticationException(providerDisplayName(provider) + " OAuth 설정이 아직 완료되지 않았습니다.");
        }
    }

    private String providerDisplayName(OAuthProvider provider) {
        return switch (provider) {
            case KAKAO -> "카카오";
            case GOOGLE -> "구글";
            case GITHUB -> "GitHub";
        };
    }

    private String sanitizeRedirectTarget(String redirectTarget) {
        if (!StringUtils.hasText(redirectTarget)) {
            return "#/search";
        }

        String trimmed = redirectTarget.trim();
        if (trimmed.startsWith("#/")) {
            return trimmed;
        }
        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            return trimmed;
        }
        return "#/search";
    }

    private MultiValueMap<String, String> formData(String... values) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        for (int index = 0; index < values.length; index += 2) {
            if (index + 1 < values.length && StringUtils.hasText(values[index + 1])) {
                formData.add(values[index], values[index + 1]);
            }
        }
        return formData;
    }

    private String extractGithubEmail(JsonNode emails) {
        if (emails == null || !emails.isArray()) {
            return null;
        }

        List<JsonNode> ordered = new java.util.ArrayList<>();
        emails.forEach(ordered::add);

        return ordered.stream()
                .filter(node -> node.path("verified").asBoolean(false))
                .sorted((left, right) -> Boolean.compare(right.path("primary").asBoolean(false), left.path("primary").asBoolean(false)))
                .map(node -> text(node.path("email")))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String requireText(JsonNode node, String fieldName, String errorMessage) {
        String value = text(node.path(fieldName));
        if (!StringUtils.hasText(value)) {
            throw new OAuthAuthenticationException(errorMessage);
        }
        return value;
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value : null;
    }
}
