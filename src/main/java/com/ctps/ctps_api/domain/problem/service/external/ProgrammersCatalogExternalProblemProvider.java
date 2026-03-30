package com.ctps.ctps_api.domain.problem.service.external;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ProgrammersProblemCatalogRepository;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemProvider;
import com.ctps.ctps_api.domain.problem.service.search.SearchIntentAnalyzer;
import com.ctps.ctps_api.domain.search.service.SearchTypeCanonicalizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProgrammersCatalogExternalProblemProvider implements ExternalProblemProvider {

    private final ProgrammersProblemCatalogRepository catalogRepository;
    private final ObjectMapper objectMapper;
    private final SearchIntentAnalyzer searchIntentAnalyzer;
    private final SearchTypeCanonicalizer searchTypeCanonicalizer;

    @Override
    public List<ExternalProblemSearchItemResponse> search(ProblemSearchRequest request) {
        if (!supportsPlatform(request)) {
            return List.of();
        }
        String keyword = searchIntentAnalyzer.resolveKeywordText(request).toLowerCase(Locale.ROOT);
        List<String> effectiveTags = searchIntentAnalyzer.resolveCanonicalTags(request);

        return catalogRepository.findAll().stream()
                .filter(item -> matchesKeyword(item, keyword))
                .filter(item -> request.getDifficulty().isEmpty()
                        || request.getDifficulty().contains(parseDifficulty(item.getDifficulty())))
                .filter(item -> matchesRequestedTags(item, effectiveTags))
                .sorted(Comparator
                        .comparingInt((com.ctps.ctps_api.domain.problem.entity.ProgrammersProblemCatalog item) ->
                                scoreMatch(item, keyword, effectiveTags, request))
                        .reversed()
                        .thenComparing(item -> item.getTitle() == null ? "" : item.getTitle()))
                .limit(15)
                .map(item -> ExternalProblemSearchItemResponse.builder()
                        .id(item.getExternalId())
                        .providerKey(providerKey())
                        .providerLabel(providerLabel())
                        .title(item.getTitle())
                        .platform("프로그래머스")
                        .problemNumber(item.getProblemNumber())
                        .difficulty(parseDifficulty(item.getDifficulty()))
                        .tags(readTags(item.getTagsJson()))
                        .externalUrl(item.getExternalUrl())
                        .summary("프로그래머스 카탈로그에서 적재한 외부 문제")
                        .recommendationReason(item.getRecommendationReason())
                        .solved(false)
                        .build())
                .toList();
    }

    @Override
    public String providerKey() {
        return "programmers";
    }

    @Override
    public String providerLabel() {
        return "프로그래머스";
    }

    private List<String> readTags(String tagsJson) {
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<>() {
            });
        } catch (Exception exception) {
            log.warn("failed to parse programmers tags json", exception);
            return List.of();
        }
    }

    private boolean matchesKeyword(
            com.ctps.ctps_api.domain.problem.entity.ProgrammersProblemCatalog item,
            String keyword
    ) {
        if (keyword.isBlank()) {
            return true;
        }

        List<String> itemTags = readTags(item.getTagsJson());
        return contains(item.getTitle(), keyword)
                || contains(item.getRecommendationReason(), keyword)
                || itemTags.stream().anyMatch(tag -> contains(tag, keyword));
    }

    private boolean matchesRequestedTags(
            com.ctps.ctps_api.domain.problem.entity.ProgrammersProblemCatalog item,
            List<String> requestedTags
    ) {
        if (requestedTags.isEmpty()) {
            return true;
        }

        List<String> itemTags = readTags(item.getTagsJson());
        return requestedTags.stream().allMatch(requestedTag ->
                searchTypeCanonicalizer.expandTagAliases(requestedTag).stream()
                        .anyMatch(alias -> itemTags.stream().anyMatch(itemTag -> contains(itemTag, alias)))
        );
    }

    private int scoreMatch(
            com.ctps.ctps_api.domain.problem.entity.ProgrammersProblemCatalog item,
            String keyword,
            List<String> requestedTags,
            ProblemSearchRequest request
    ) {
        int score = 0;
        String normalizedTitle = normalize(item.getTitle());
        String normalizedReason = normalize(item.getRecommendationReason());
        List<String> itemTags = readTags(item.getTagsJson());

        if (StringUtils.hasText(keyword)) {
            if (normalizedTitle.equals(keyword)) {
                score += 90;
            } else if (normalizedTitle.contains(keyword)) {
                score += 55;
            }
            if (normalizedReason.contains(keyword)) {
                score += 18;
            }
            score += itemTags.stream()
                    .mapToInt(tag -> scoreTagKeywordMatch(tag, keyword))
                    .sum();
        }

        for (String requestedTag : requestedTags) {
            boolean matched = searchTypeCanonicalizer.expandTagAliases(requestedTag).stream()
                    .anyMatch(alias -> itemTags.stream().anyMatch(itemTag -> contains(itemTag, alias)));
            if (matched) {
                score += 40;
            }
        }

        if (!request.getDifficulty().isEmpty() && request.getDifficulty().contains(parseDifficulty(item.getDifficulty()))) {
            score += 8;
        }

        if (StringUtils.hasText(item.getRecommendationReason())) {
            score += 2;
        }
        return score;
    }

    private int scoreTagKeywordMatch(String tag, String keyword) {
        String normalizedTag = normalize(tag);
        if (normalizedTag.equals(keyword)) {
            return 40;
        }
        if (normalizedTag.contains(keyword)) {
            return 24;
        }
        return 0;
    }

    private Problem.Difficulty parseDifficulty(String difficulty) {
        return switch (difficulty == null ? "" : difficulty.toLowerCase(Locale.ROOT)) {
            case "easy" -> Problem.Difficulty.easy;
            case "hard" -> Problem.Difficulty.hard;
            default -> Problem.Difficulty.medium;
        };
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value) && normalize(value).contains(normalize(keyword));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean supportsPlatform(ProblemSearchRequest request) {
        return request.getPlatform().isEmpty()
                || request.getPlatform().stream().anyMatch(platform -> "프로그래머스".equals(platform));
    }
}
