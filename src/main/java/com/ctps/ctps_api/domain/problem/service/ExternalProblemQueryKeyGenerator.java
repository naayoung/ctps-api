package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.service.search.SearchIntentAnalyzer;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExternalProblemQueryKeyGenerator {

    private static final String QUERY_KEY_VERSION = "v2";

    private final SearchIntentAnalyzer searchIntentAnalyzer;

    public ExternalProblemQueryKeyGenerator(SearchIntentAnalyzer searchIntentAnalyzer) {
        this.searchIntentAnalyzer = searchIntentAnalyzer;
    }

    public ExternalProblemQueryKeyGenerator() {
        this(new SearchIntentAnalyzer());
    }

    public String generate(String providerName, ProblemSearchRequest request) {
        return "version=" + QUERY_KEY_VERSION
                + "|provider=" + providerName
                + "|keyword=" + nullToEmpty(searchIntentAnalyzer.resolveKeywordText(request))
                + "|platform=" + normalizeList(request.getPlatform())
                + "|difficulty=" + normalizeList(request.getDifficulty().stream().map(Enum::name).toList())
                + "|tags=" + normalizeList(searchIntentAnalyzer.resolveCanonicalTags(request))
                + "|result=" + normalizeList(request.getResult().stream().map(Enum::name).toList())
                + "|needsReview=" + request.getNeedsReview();
    }

    private String normalizeList(List<String> values) {
        return values.stream()
                .sorted(Comparator.naturalOrder())
                .map(this::nullToEmpty)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
