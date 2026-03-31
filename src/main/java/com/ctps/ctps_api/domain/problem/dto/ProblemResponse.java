package com.ctps.ctps_api.domain.problem.dto;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProblemResponse {

    private String id;
    private String platform;
    private String title;
    private String number;
    private String link;
    private List<String> tags;
    private Problem.Difficulty difficulty;
    private String memo;
    private Problem.Result result;
    private boolean needsReview;
    private LocalDate reviewedAt;
    private List<LocalDate> reviewHistory;
    private LocalDateTime createdAt;
    private List<LocalDate> solvedDates;
    private List<LocalDateTime> solveHistory;
    private int solveCount;
    private LocalDate lastSolvedAt;
    private boolean bookmarked;

    public static ProblemResponse from(Problem problem) {
        List<LocalDateTime> solveHistory = resolveSolveHistory(problem);
        return ProblemResponse.builder()
                .id(String.valueOf(problem.getId()))
                .platform(problem.getPlatform())
                .title(problem.getTitle())
                .number(problem.getNumber())
                .link(problem.getLink())
                .tags(problem.getTags() == null ? List.of() : new ArrayList<>(problem.getTags()))
                .difficulty(problem.getDifficulty())
                .memo(problem.getMemo())
                .result(problem.getResult())
                .needsReview(problem.isNeedsReview())
                .reviewedAt(problem.getReviewedAt())
                .reviewHistory(problem.getReviewHistory() == null ? List.of() : new ArrayList<>(problem.getReviewHistory()))
                .createdAt(problem.getCreatedAt())
                .solvedDates(problem.getSolvedDates() == null ? List.of() : new ArrayList<>(problem.getSolvedDates()))
                .solveHistory(solveHistory)
                .solveCount(solveHistory.size())
                .lastSolvedAt(problem.getLastSolvedAt())
                .bookmarked(problem.isBookmarked())
                .build();
    }

    private static List<LocalDateTime> resolveSolveHistory(Problem problem) {
        if (problem.getSolveHistory() != null && !problem.getSolveHistory().isEmpty()) {
            return new ArrayList<>(problem.getSolveHistory());
        }

        if (problem.getSolvedDates() == null || problem.getSolvedDates().isEmpty()) {
            return List.of();
        }

        return problem.getSolvedDates().stream()
                .map(LocalDate::atStartOfDay)
                .toList();
    }
}
