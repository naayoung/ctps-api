package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Component
@Order(10)
public class SolvedAcProviderScoreResolver implements ExternalProblemProviderScoreResolver {

    private static final String PROVIDER_NAME = "solvedac";

    @Override
    public boolean supports(String providerName) {
        return PROVIDER_NAME.equals(providerName);
    }

    @Override
    public List<ProviderScoreSignal> resolve(
            String providerName,
            ProcessedSearchQuery query,
            List<ExternalProblemSearchItemResponse> items
    ) {
        List<ProviderScoreSignal> signals = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            signals.add(ProviderScoreSignal.builder()
                    .providerName(providerName)
                    .scoreSource(ProviderScoreSource.RANK_POSITION)
                    .rank(index + 1)
                    .rawScore((double) (items.size() - index))
                    .build());
        }
        return signals;
    }
}
