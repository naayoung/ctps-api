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
    private boolean bookmarked;
    private String memoSummary;
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
                .needsReview(problem.isNeedsReview() && !problem.isBookmarked())
                .bookmarked(problem.isBookmarked())
                .memoSummary(summarizeMemo(problem.getMemo()))
                .lastSolvedAt(problem.getLastSolvedAt())
                .createdAt(problem.getCreatedAt())
                .build();
    }

    private static String summarizeMemo(String memo) {
        if (memo == null || memo.isBlank()) {
            return "";
        }
        String normalized = memo.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 117) + "...";
    }
}
