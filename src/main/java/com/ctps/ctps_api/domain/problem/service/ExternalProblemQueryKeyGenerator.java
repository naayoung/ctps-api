package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExternalProblemQueryKeyGenerator {

    public String generate(String providerName, ProblemSearchRequest request) {
        return providerName
                + "|keyword=" + nullToEmpty(request.getKeyword())
                + "|platform=" + normalizeList(request.getPlatform())
                + "|difficulty=" + normalizeList(request.getDifficulty().stream().map(Enum::name).toList())
                + "|tags=" + normalizeList(request.getTags())
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
