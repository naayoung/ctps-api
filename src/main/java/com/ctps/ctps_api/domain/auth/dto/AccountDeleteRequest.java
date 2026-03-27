package com.ctps.ctps_api.domain.auth.dto;

import lombok.Getter;

@Getter
public class AccountDeleteRequest {

    private String currentPassword;
    private String confirmationText;
}
