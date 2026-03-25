package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;

public interface ProblemSearchScorer {

    ProblemSearchScore score(
            ProcessedSearchQuery query,
            ExternalProblemSearchItemResponse item,
            ProviderScoreSignal providerScoreSignal
    );
}
