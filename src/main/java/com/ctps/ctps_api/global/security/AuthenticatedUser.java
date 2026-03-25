package com.ctps.ctps_api.global.security;

import com.ctps.ctps_api.domain.auth.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthenticatedUser {

    private Long id;
    private String username;
    private String displayName;

    public static AuthenticatedUser from(User user) {
        return AuthenticatedUser.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .build();
    }
}
