package com.ctps.ctps_api.domain.problem.service.search;

import java.util.List;

public interface ExternalProblemProviderScoreNormalizer {

    List<ProviderScoreSignal> normalize(List<ProviderScoreSignal> signals);
}
