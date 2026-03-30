package com.ctps.ctps_api.domain.problem.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProblemMetadataResolveRequest {

    private String link;
    private String platform;
    private String number;
}
