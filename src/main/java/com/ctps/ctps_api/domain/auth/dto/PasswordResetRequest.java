package com.ctps.ctps_api.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class PasswordResetRequest {

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "올바른 이메일 형식을 입력해 주세요.")
    private String email;

    @NotBlank(message = "표시 이름을 입력해 주세요.")
    @Size(max = 100, message = "표시 이름은 100자 이하로 입력해 주세요.")
    private String displayName;
}
