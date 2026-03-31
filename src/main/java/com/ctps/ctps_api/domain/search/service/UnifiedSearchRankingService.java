package com.ctps.ctps_api.domain.search.service;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchSortOption;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.search.ProcessedSearchQuery;
import com.ctps.ctps_api.domain.search.dto.SearchItemSource;
import com.ctps.ctps_api.domain.search.dto.UnifiedSearchItemResponse;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class UnifiedSearchRankingService {

    public List<UnifiedSearchRankingResult> rank(
            List<UnifiedSearchItemResponse> items,
            ProcessedSearchQuery processedQuery,
            ProblemSearchSortOption sortOption
    ) {
        return items.stream()
                .map(item -> score(item, processedQuery))
                .sorted(sortComparator(sortOption))
                .toList();
    }

    private UnifiedSearchRankingResult score(UnifiedSearchItemResponse item, ProcessedSearchQuery query) {
        int keywordScore = calculateKeywordScore(item, query);
        int tagScore = calculateTagScore(item, query);
        int platformScore = calculatePlatformScore(item, query);
        int difficultyScore = calculateDifficultyScore(item, query);
        int internalPriorityScore = item.getSource() == SearchItemSource.INTERNAL ? 3 : 0;
        int bookmarkScore = item.isBookmarked() ? 2 : 0;
        int reviewPriorityScore = item.isNeedsReview() ? 2 : 0;
        int ruleScore = keywordScore + tagScore + platformScore + difficultyScore + internalPriorityScore + bookmarkScore + reviewPriorityScore;
        int normalizedProviderScore = item.getProviderNormalizedScore() == null
                ? 0
                : (int) Math.round(item.getProviderNormalizedScore() * 4);
        int totalScore = ruleScore + normalizedProviderScore;

        return UnifiedSearchRankingResult.builder()
                .item(UnifiedSearchItemResponse.builder()
                        .id(item.getId())
                        .source(item.getSource())
                        .sourceLabel(item.getSourceLabel())
                        .providerKey(item.getProviderKey())
                        .providerLabel(item.getProviderLabel())
                        .title(item.getTitle())
                        .platform(item.getPlatform())
                        .problemNumber(item.getProblemNumber())
                        .difficulty(item.getDifficulty())
                        .difficultyLabel(item.getDifficultyLabel())
                        .tags(item.getTags())
                        .summary(item.getSummary())
                        .description(item.getDescription())
                        .externalUrl(item.getExternalUrl())
                        .result(item.getResult())
                        .needsReview(item.isNeedsReview())
                        .bookmarked(item.isBookmarked())
                        .solved(item.isSolved())
                        .lastSolvedAt(item.getLastSolvedAt())
                        .createdAt(item.getCreatedAt())
                        .providerScore(item.getProviderScore())
                        .providerNormalizedScore(item.getProviderNormalizedScore())
                        .ruleScore(ruleScore)
                        .totalScore(totalScore)
                        .build())
                .totalScore(totalScore)
                .ruleScore(ruleScore)
                .build();
    }

    private Comparator<UnifiedSearchRankingResult> sortComparator(ProblemSearchSortOption sortOption) {
        return switch (sortOption) {
            case LAST_SOLVED_AT -> Comparator
                    .comparing((UnifiedSearchRankingResult result) -> result.getItem().getSource() != SearchItemSource.INTERNAL)
                    .thenComparing(
                            (UnifiedSearchRankingResult result) -> activityDate(result.getItem()),
                            Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(UnifiedSearchRankingResult::getTotalScore, Comparator.reverseOrder())
                    .thenComparing(result -> safeLower(result.getItem().getTitle()));
            case DIFFICULTY -> Comparator
                    .comparingInt((UnifiedSearchRankingResult result) -> difficultyRank(result.getItem().getDifficulty())).reversed()
                    .thenComparing(UnifiedSearchRankingResult::getTotalScore, Comparator.reverseOrder());
            default -> Comparator
                    .comparingInt(UnifiedSearchRankingResult::getTotalScore).reversed()
                    .thenComparing(result -> result.getItem().getSource() == SearchItemSource.EXTERNAL)
                    .thenComparing(result -> safeLower(result.getItem().getTitle()));
        };
    }

    private int calculateKeywordScore(UnifiedSearchItemResponse item, ProcessedSearchQuery query) {
        if (!StringUtils.hasText(query.getNormalizedKeyword())) {
            return 0;
        }

        String title = normalize(item.getTitle());
        if (title.contains(query.getNormalizedKeyword())) {
            return 10;
        }

        boolean tokenMatch = query.getKeywordTokens().stream().anyMatch(title::contains);
        if (tokenMatch) {
            return 6;
        }

        if (containsKeywordInTags(item, query)) {
            return 4;
        }

        String description = normalize(item.getSummary()) + " " + normalize(item.getDescription());
        return query.getKeywordTokens().stream().anyMatch(description::contains) ? 3 : 0;
    }

    private int calculateTagScore(UnifiedSearchItemResponse item, ProcessedSearchQuery query) {
        if (query.getNormalizedTags().isEmpty() || item.getTags() == null) {
            return 0;
        }
        long matched = query.getNormalizedTags().stream()
                .filter(tag -> item.getTags().stream().map(this::normalize).anyMatch(candidate -> candidate.contains(tag)))
                .count();
        if (matched >= 2) return 5;
        if (matched == 1) return 3;
        return 0;
    }

    private int calculatePlatformScore(UnifiedSearchItemResponse item, ProcessedSearchQuery query) {
        if (query.getNormalizedPlatforms().isEmpty()) {
            return 0;
        }
        String normalizedPlatform = normalize(item.getPlatform());
        return query.getNormalizedPlatforms().stream().anyMatch(normalizedPlatform::equals) ? 2 : 0;
    }

    private int calculateDifficultyScore(UnifiedSearchItemResponse item, ProcessedSearchQuery query) {
        if (item.getDifficulty() == null || query.getRequestedDifficulties().isEmpty()) {
            return 0;
        }
        return query.getRequestedDifficulties().stream()
                .mapToInt(requested -> {
                    int distance = Math.abs(difficultyRank(requested) - difficultyRank(item.getDifficulty()));
                    if (distance == 0) return 3;
                    if (distance == 1) return 1;
                    return 0;
                })
                .max()
                .orElse(0);
    }

    private int difficultyRank(Problem.Difficulty difficulty) {
        if (difficulty == null) return 0;
        return switch (difficulty) {
            case easy -> 1;
            case medium -> 2;
            case hard -> 3;
        };
    }

    private LocalDateTime activityDate(UnifiedSearchItemResponse item) {
        if (item.getLastSolvedAt() != null) {
            return item.getLastSolvedAt().atStartOfDay();
        }
        if (item.getCreatedAt() != null) {
            return item.getCreatedAt();
        }
        return null;
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

    private boolean containsKeywordInTags(UnifiedSearchItemResponse item, ProcessedSearchQuery query) {
        if (item.getTags() == null || item.getTags().isEmpty()) {
            return false;
        }

        List<String> normalizedTags = item.getTags().stream()
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .toList();

        if (normalizedTags.stream().anyMatch(tag -> tag.contains(query.getNormalizedKeyword()))) {
            return true;
        }

        return query.getKeywordTokens().stream()
                .anyMatch(token -> normalizedTags.stream().anyMatch(tag -> tag.contains(token)));
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
