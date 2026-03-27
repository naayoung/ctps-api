package com.ctps.ctps_api.domain.auth.dto;

import com.ctps.ctps_api.domain.auth.entity.AuthProvider;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FindUsernameResponse {

    private String status;
    private String maskedUsername;
    private AuthProvider authProvider;
}
