package com.ctps.ctps_api.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SignUpResponse {

    private String status;
    private String verificationToken;
    private String verificationLink;
    private Long expiresInMinutes;
}
