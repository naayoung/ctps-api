package com.ctps.ctps_api.domain.auth.dto;

import com.ctps.ctps_api.domain.auth.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private String id;
    private String username;
    private String displayName;

    public static AuthResponse from(User user) {
        return AuthResponse.builder()
                .id(String.valueOf(user.getId()))
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .build();
    }
}
