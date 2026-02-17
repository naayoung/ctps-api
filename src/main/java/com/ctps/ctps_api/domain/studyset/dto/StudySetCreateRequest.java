package com.ctps.ctps_api.domain.studyset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;

@Getter
public class StudySetCreateRequest {

    @NotBlank
    private String name;

    @NotNull
    private List<String> problemIds;
}
