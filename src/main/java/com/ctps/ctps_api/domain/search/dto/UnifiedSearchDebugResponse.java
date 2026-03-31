package com.ctps.ctps_api.domain.search.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UnifiedSearchDebugResponse {

    private int exactCandidatesCount;
    private int partialCandidatesCount;
    private int fallbackCandidatesCount;
    private int deduplicatedCount;
    private Map<SearchRankingType, Long> rankingTypeCounts;
}
