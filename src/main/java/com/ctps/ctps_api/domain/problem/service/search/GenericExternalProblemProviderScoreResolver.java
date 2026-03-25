package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1000)
public class GenericExternalProblemProviderScoreResolver implements ExternalProblemProviderScoreResolver {

    @Override
    public boolean supports(String providerName) {
        return true;
    }

    @Override
    public List<ProviderScoreSignal> resolve(
            String providerName,
            ProcessedSearchQuery query,
            List<ExternalProblemSearchItemResponse> items
    ) {
        return items.stream()
                .map(item -> ProviderScoreSignal.builder()
                        .providerName(providerName)
                        .scoreSource(ProviderScoreSource.NONE)
                        .rawScore(0.0)
                        .normalizedScore(0.0)
                        .build())
                .toList();
    }
}
