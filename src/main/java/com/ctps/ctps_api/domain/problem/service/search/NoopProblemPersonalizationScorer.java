package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import org.springframework.stereotype.Component;

@Component
public class NoopProblemPersonalizationScorer implements ProblemPersonalizationScorer {

    @Override
    public int score(ProcessedSearchQuery query, ExternalProblemSearchItemResponse item) {
        // Future extension point: map external problem identifiers to internal solve history
        // and apply unsolved / failed / needs-review bonuses here.
        return 0;
    }
}
