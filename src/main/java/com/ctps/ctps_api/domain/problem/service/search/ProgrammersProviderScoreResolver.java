package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(10)
public class ProgrammersProviderScoreResolver implements ExternalProblemProviderScoreResolver {

    private static final String PROVIDER_NAME = "programmers";

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
        for (ExternalProblemSearchItemResponse item : items) {
            signals.add(ProviderScoreSignal.builder()
                    .providerName(providerName)
                    .scoreSource(ProviderScoreSource.HEURISTIC)
                    .rawScore(calculateHeuristic(query, item))
                    .build());
        }
        return signals;
    }

    private double calculateHeuristic(ProcessedSearchQuery query, ExternalProblemSearchItemResponse item) {
        double score = 0.0;
        String title = normalize(item.getTitle());
        String tags = normalize(String.join(" ", item.getTags() == null ? List.of() : item.getTags()));
        String reason = normalize(item.getRecommendationReason());

        if (StringUtils.hasText(query.getNormalizedKeyword()) && title.contains(query.getNormalizedKeyword())) {
            score += 1.0;
        } else if (query.getKeywordTokens().stream().anyMatch(title::contains)) {
            score += 0.7;
        }

        long tagMatches = query.getNormalizedTags().stream().filter(tags::contains).count();
        score += Math.min(tagMatches, 2) * 0.3;

        if (query.getKeywordTokens().stream().anyMatch(reason::contains)) {
            score += 0.2;
        }

        if (!query.getRequestedDifficulties().isEmpty() && query.getRequestedDifficulties().contains(item.getDifficulty())) {
            score += 0.3;
        }

        return score;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
