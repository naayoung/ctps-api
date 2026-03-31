package com.ctps.ctps_api.domain.search.service;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SearchResultAssembler {

    public List<UnifiedSearchRankingResult> assemble(List<UnifiedSearchRankingResult> rankedResults, int targetSize) {
        return rankedResults.stream()
                .limit(targetSize)
                .toList();
    }
}
