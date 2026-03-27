package com.ctps.ctps_api.domain.auth.entity;

public enum AuthProvider {
    LOCAL,
    KAKAO,
    GOOGLE,
    GITHUB;

    public static AuthProvider fromOAuthProvider(OAuthProvider provider) {
        return switch (provider) {
            case KAKAO -> KAKAO;
            case GOOGLE -> GOOGLE;
            case GITHUB -> GITHUB;
        };
    }
}
