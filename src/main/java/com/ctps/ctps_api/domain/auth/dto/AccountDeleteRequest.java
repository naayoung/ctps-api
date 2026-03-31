package com.ctps.ctps_api.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class AccountDeleteRequest {

    @Size(max = 72, message = "현재 비밀번호 형식이 올바르지 않습니다.")
    private String currentPassword;

    @NotBlank(message = "확인 문구를 입력해 주세요.")
    @Size(max = 20, message = "확인 문구 형식이 올바르지 않습니다.")
    private String confirmationText;
}
