package com.ctps.ctps_api.domain.auth.dto;

import lombok.Getter;
import jakarta.validation.constraints.NotBlank;

@Getter
public class AuthRequest {

    @NotBlank
    private String username;
    @NotBlank
    private String password;
}
