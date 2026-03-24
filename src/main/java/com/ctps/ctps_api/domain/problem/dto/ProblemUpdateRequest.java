package com.ctps.ctps_api.domain.problem.dto;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;

@Getter
public class ProblemUpdateRequest {

    private String platform;
    private String title;
    private String number;
    private String link;
    private List<String> tags;
    private Problem.Difficulty difficulty;
    private String memo;
    private Problem.Result result;
    private Boolean needsReview;
    private LocalDate reviewedAt;
    private List<LocalDate> reviewHistory;
    private List<LocalDate> solvedDates;
    private LocalDate lastSolvedAt;
    private Boolean bookmarked;
}
