package com.ctps.ctps_api.domain.problem.dto;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProblemMetadataResponse {

    private String platform;
    private String title;
    private String number;
    private String link;
    private List<String> tags;
    private Problem.Difficulty difficulty;
    private boolean metadataFound;
}
