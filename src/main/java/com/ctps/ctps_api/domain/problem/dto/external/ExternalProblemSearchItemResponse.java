package com.ctps.ctps_api.domain.problem.dto.external;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExternalProblemSearchItemResponse {

    private String id;
    private String title;
    private String platform;
    private String problemNumber;
    private Problem.Difficulty difficulty;
    private List<String> tags;
    private String externalUrl;
    private String recommendationReason;
    private boolean solved;
}
