package com.ctps.ctps_api.domain.search.dto;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.search.entity.ProblemInteractionEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;

@Getter
public class ProblemInteractionEventRequest {

    @NotBlank
    private String problemRef;

    @NotNull
    private SearchItemSource source;

    @NotBlank
    private String platform;

    @NotBlank
    private String problemNumber;

    @NotNull
    private Problem.Difficulty difficulty;

    @NotNull
    private List<String> tags;

    @NotNull
    private ProblemInteractionEvent.EventType eventType;

    private String sourceQuery;
}
