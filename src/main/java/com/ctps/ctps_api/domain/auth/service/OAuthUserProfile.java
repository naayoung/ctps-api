package com.ctps.ctps_api.domain.auth.service;

import com.ctps.ctps_api.domain.auth.entity.OAuthProvider;

public record OAuthUserProfile(
        OAuthProvider provider,
        String providerUserId,
        String email,
        String nickname,
        String profileImageUrl
) {
}
