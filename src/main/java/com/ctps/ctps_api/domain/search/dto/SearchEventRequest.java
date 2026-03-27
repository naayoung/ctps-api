package com.ctps.ctps_api.domain.search.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class SearchEventRequest {

    @NotBlank
    private String query;
}
