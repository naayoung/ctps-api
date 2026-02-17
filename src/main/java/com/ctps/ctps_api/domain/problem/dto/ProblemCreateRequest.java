package com.ctps.ctps_api.domain.problem.dto;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;

@Getter
public class ProblemCreateRequest {

    @NotBlank
    private String platform;

    @NotBlank
    private String number;

    private String link;

    @NotNull
    private List<String> tags;

    @NotNull
    private Problem.Difficulty difficulty;

    @NotNull
    private String memo;

    private Problem.Result result;

    private boolean needsReview;

    private LocalDate reviewedAt;

    @NotNull
    private List<LocalDate> reviewHistory;

    @NotNull
    private List<LocalDate> solvedDates;

    private LocalDate lastSolvedAt;

    private boolean bookmarked;
}
