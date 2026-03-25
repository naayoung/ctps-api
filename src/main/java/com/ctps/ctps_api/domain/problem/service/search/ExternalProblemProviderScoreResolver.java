package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import java.util.List;

public interface ExternalProblemProviderScoreResolver {

    boolean supports(String providerName);

    List<ProviderScoreSignal> resolve(
            String providerName,
            ProcessedSearchQuery query,
            List<ExternalProblemSearchItemResponse> items
    );
}
