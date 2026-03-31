package com.ctps.ctps_api.domain.problem.dto;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.entity.ProblemSolveHistoryEntry;
import com.ctps.ctps_api.global.time.DateTimeSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProblemSolveHistoryItemResponse {

    private Long id;
    private OffsetDateTime solvedAt;
    private ProblemSolveHistoryEntry.ActivityType activityType;
    private Problem.Result result;
    private String memo;
    private boolean metadataFallback;

    public static ProblemSolveHistoryItemResponse from(ProblemSolveHistoryEntry entry) {
        return ProblemSolveHistoryItemResponse.builder()
                .id(entry.getId())
                .solvedAt(DateTimeSupport.asUtcOffsetDateTime(entry.getSolvedAt()))
                .activityType(entry.getActivityType())
                .result(entry.getResult())
                .memo(entry.getMemo())
                .metadataFallback(false)
                .build();
    }

    public static ProblemSolveHistoryItemResponse fallback(LocalDateTime solvedAt) {
        return ProblemSolveHistoryItemResponse.builder()
                .id(null)
                .solvedAt(DateTimeSupport.asUtcOffsetDateTime(solvedAt))
                .activityType(ProblemSolveHistoryEntry.ActivityType.solve)
                .result(Problem.Result.success)
                .memo("")
                .metadataFallback(true)
                .build();
    }

    public static ProblemSolveHistoryItemResponse fallback(LocalDate solvedDate) {
        return ProblemSolveHistoryItemResponse.builder()
                .id(null)
                .solvedAt(DateTimeSupport.asSeoulStartOfDay(solvedDate))
                .activityType(ProblemSolveHistoryEntry.ActivityType.solve)
                .result(Problem.Result.success)
                .memo("")
                .metadataFallback(true)
                .build();
    }
}
