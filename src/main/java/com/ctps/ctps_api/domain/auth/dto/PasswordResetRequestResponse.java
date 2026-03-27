package com.ctps.ctps_api.domain.auth.dto;

import com.ctps.ctps_api.domain.auth.entity.AuthProvider;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PasswordResetRequestResponse {

    private String status;
    private AuthProvider authProvider;
    private String resetToken;
    private String resetLink;
    private Long expiresInMinutes;
}
