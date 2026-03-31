package com.ctps.ctps_api.domain.search.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UnifiedSearchScoreBreakdown {

    private Integer baseScore;
    private Integer matchScore;
    private Integer tagScore;
    private Integer typeScore;
    private Integer difficultyScore;
    private Integer platformScore;
    private Integer userPreferenceScore;
    private Integer freshnessScore;
    private Integer penaltyScore;
    private Integer finalScore;
}
