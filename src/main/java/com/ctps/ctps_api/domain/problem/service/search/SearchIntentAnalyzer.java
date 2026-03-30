package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.search.service.SearchTypeCanonicalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SearchIntentAnalyzer {

    private final SearchTypeCanonicalizer searchTypeCanonicalizer;

    public SearchIntentAnalyzer(SearchTypeCanonicalizer searchTypeCanonicalizer) {
        this.searchTypeCanonicalizer = searchTypeCanonicalizer;
    }

    public SearchIntentAnalyzer() {
        this(new SearchTypeCanonicalizer());
    }

    public List<String> resolveCanonicalTags(ProblemSearchRequest request) {
        Set<String> resolved = new LinkedHashSet<>();
        resolved.addAll(searchTypeCanonicalizer.canonicalizeTags(request.getTags()));
        resolved.addAll(inferCanonicalTags(request.getKeyword()));
        return List.copyOf(resolved);
    }

    public String resolveKeywordText(ProblemSearchRequest request) {
        return isTagOnlySearch(request) ? "" : normalizeKeyword(request.getKeyword());
    }

    public boolean isTagOnlySearch(ProblemSearchRequest request) {
        if (!request.getTags().isEmpty() && !StringUtils.hasText(request.getKeyword())) {
            return true;
        }

        return !inferCanonicalTags(request.getKeyword()).isEmpty();
    }

    List<String> inferCanonicalTags(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (!StringUtils.hasText(normalizedKeyword)) {
            return List.of();
        }

        if (searchTypeCanonicalizer.isKnownTagAlias(normalizedKeyword)) {
            return List.of(searchTypeCanonicalizer.canonicalizeTag(normalizedKeyword));
        }

        List<String> segments = Arrays.stream(normalizedKeyword.split("[,|/\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();

        if (segments.size() < 2) {
            return List.of();
        }

        List<String> resolved = new ArrayList<>();
        for (String segment : segments) {
            if (!searchTypeCanonicalizer.isKnownTagAlias(segment)) {
                return List.of();
            }
            resolved.add(searchTypeCanonicalizer.canonicalizeTag(segment));
        }

        return resolved.stream().distinct().toList();
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }
}
