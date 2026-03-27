package com.ctps.ctps_api.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class PasswordResetConfirmRequest {

    @NotBlank(message = "재설정 토큰을 입력해 주세요.")
    private String token;

    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    @Size(min = 8, max = 72, message = "새 비밀번호는 8자 이상 72자 이하로 입력해 주세요.")
    private String newPassword;

    @NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
    private String newPasswordConfirm;
}
