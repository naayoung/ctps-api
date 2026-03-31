package com.ctps.ctps_api.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class EmailVerificationConfirmRequest {

    @NotBlank(message = "이메일 인증 토큰을 입력해 주세요.")
    private String token;
}
