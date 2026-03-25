package com.ctps.ctps_api.domain.search.service;

import com.ctps.ctps_api.domain.search.dto.UnifiedSearchItemResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UnifiedSearchRankingResult {

    private UnifiedSearchItemResponse item;
    private int totalScore;
    private int ruleScore;
}
