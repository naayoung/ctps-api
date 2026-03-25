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
public class LeetCodeProviderScoreResolver implements ExternalProblemProviderScoreResolver {

    private static final String PROVIDER_NAME = "LeetCodeExternalProblemProvider";

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
        String slug = normalize(extractSlug(item.getExternalUrl()));

        if (StringUtils.hasText(query.getNormalizedKeyword()) && title.contains(query.getNormalizedKeyword())) {
            score += 1.0;
        } else if (query.getKeywordTokens().stream().anyMatch(title::contains)) {
            score += 0.7;
        }

        if (query.getKeywordTokens().stream().anyMatch(slug::contains)) {
            score += 0.4;
        }

        if (!query.getRequestedDifficulties().isEmpty() && query.getRequestedDifficulties().contains(item.getDifficulty())) {
            score += 0.3;
        }

        return score;
    }

    private String extractSlug(String externalUrl) {
        if (!StringUtils.hasText(externalUrl)) {
            return "";
        }
        String[] parts = externalUrl.split("/");
        for (int index = parts.length - 1; index >= 0; index--) {
            if (StringUtils.hasText(parts[index])) {
                return parts[index];
            }
        }
        return "";
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
