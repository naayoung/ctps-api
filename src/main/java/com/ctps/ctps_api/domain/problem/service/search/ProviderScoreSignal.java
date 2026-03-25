package com.ctps.ctps_api.domain.problem.service.search;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class ProviderScoreSignal {

    private String providerName;
    private ProviderScoreSource scoreSource;
    private Double rawScore;
    private Double normalizedScore;
    private Integer rank;
}
