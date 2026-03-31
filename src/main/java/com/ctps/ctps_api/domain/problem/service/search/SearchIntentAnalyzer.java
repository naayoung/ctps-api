package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.search.service.SearchTypeCanonicalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SearchIntentAnalyzer {

    private static final Map<String, List<String>> PLATFORM_ALIASES = new LinkedHashMap<>();
    private static final Set<String> GENERIC_NOISE_TOKENS = Set.of(
            "문제", "문제들", "추천", "유형", "찾기", "검색", "문항"
    );

    static {
        PLATFORM_ALIASES.put("백준", List.of("백준", "boj", "baekjoon"));
        PLATFORM_ALIASES.put("프로그래머스", List.of("프로그래머스", "programmers"));
        PLATFORM_ALIASES.put("리트코드", List.of("리트코드", "leetcode", "leetCode"));
    }

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
        String normalizedKeyword = stripNoiseTokens(request.getKeyword());
        return inferCanonicalTags(normalizedKeyword).isEmpty() ? normalizedKeyword : "";
    }

    public boolean isTagOnlySearch(ProblemSearchRequest request) {
        if (!request.getTags().isEmpty() && !StringUtils.hasText(request.getKeyword())) {
            return true;
        }

        return !inferCanonicalTags(request.getKeyword()).isEmpty();
    }

    List<String> inferCanonicalTags(String keyword) {
        String normalizedKeyword = stripNoiseTokens(keyword);
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

    private String stripNoiseTokens(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (!StringUtils.hasText(normalizedKeyword)) {
            return "";
        }

        return Arrays.stream(normalizedKeyword.split("\\s+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(token -> !isPlatformAlias(token))
                .filter(token -> !GENERIC_NOISE_TOKENS.contains(token.toLowerCase(Locale.ROOT)))
                .collect(java.util.stream.Collectors.joining(" "))
                .trim();
    }

    private boolean isPlatformAlias(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return PLATFORM_ALIASES.values().stream()
                .flatMap(List::stream)
                .map(alias -> alias.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }
}
