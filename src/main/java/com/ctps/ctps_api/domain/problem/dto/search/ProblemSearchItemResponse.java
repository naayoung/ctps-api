package com.ctps.ctps_api.domain.problem.dto.search;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProblemSearchItemResponse {

    private String id;
    private String title;
    private String platform;
    private String problemNumber;
    private Problem.Difficulty difficulty;
    private List<String> tags;
    private Problem.Result result;
    private boolean needsReview;
    private LocalDate lastSolvedAt;
    private LocalDateTime createdAt;

    public static ProblemSearchItemResponse from(Problem problem) {
        return ProblemSearchItemResponse.builder()
                .id(String.valueOf(problem.getId()))
                .title(problem.getTitle())
                .platform(problem.getPlatform())
                .problemNumber(problem.getNumber())
                .difficulty(problem.getDifficulty())
                .tags(problem.getTags())
                .result(problem.getResult())
                .needsReview(problem.isNeedsReview())
                .lastSolvedAt(problem.getLastSolvedAt())
                .createdAt(problem.getCreatedAt())
                .build();
    }
}
