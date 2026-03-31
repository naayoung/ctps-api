package com.ctps.ctps_api.domain.problem.dto;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.entity.ProblemSolveHistoryEntry;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProblemSolveHistoryItemResponse {

    private Long id;
    private LocalDateTime solvedAt;
    private Problem.Result result;
    private String memo;
    private boolean metadataFallback;

    public static ProblemSolveHistoryItemResponse from(ProblemSolveHistoryEntry entry) {
        return ProblemSolveHistoryItemResponse.builder()
                .id(entry.getId())
                .solvedAt(entry.getSolvedAt())
                .result(entry.getResult())
                .memo(entry.getMemo())
                .metadataFallback(false)
                .build();
    }

    public static ProblemSolveHistoryItemResponse fallback(LocalDateTime solvedAt) {
        return ProblemSolveHistoryItemResponse.builder()
                .id(null)
                .solvedAt(solvedAt)
                .result(Problem.Result.success)
                .memo("")
                .metadataFallback(true)
                .build();
    }
}
