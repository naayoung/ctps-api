package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class ProviderScoredExternalProblem {

    private ExternalProblemSearchItemResponse item;
    private ProviderScoreSignal providerScoreSignal;
    private ProblemSearchScore searchScore;
}
