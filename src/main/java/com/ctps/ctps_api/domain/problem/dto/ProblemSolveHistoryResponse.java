package com.ctps.ctps_api.domain.problem.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProblemSolveHistoryResponse {

    private Long problemId;
    private String problemTitle;
    private int totalSolveCount;
    private List<ProblemSolveHistoryItemResponse> entries;
}
