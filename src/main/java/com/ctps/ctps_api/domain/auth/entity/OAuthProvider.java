package com.ctps.ctps_api.domain.auth.entity;

import java.util.Arrays;

public enum OAuthProvider {
    KAKAO,
    GOOGLE,
    GITHUB;

    public static OAuthProvider from(String value) {
        return Arrays.stream(values())
                .filter(provider -> provider.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 OAuth provider입니다: " + value));
    }
}
