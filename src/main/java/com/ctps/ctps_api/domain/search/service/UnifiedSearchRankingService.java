package com.ctps.ctps_api.domain.search.service;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchSortOption;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.search.ProcessedSearchQuery;
import com.ctps.ctps_api.domain.search.dto.SearchCandidateOrigin;
import com.ctps.ctps_api.domain.search.dto.SearchItemSource;
import com.ctps.ctps_api.domain.search.dto.SearchRankingType;
import com.ctps.ctps_api.domain.search.dto.UnifiedSearchItemResponse;
import com.ctps.ctps_api.domain.search.dto.UnifiedSearchScoreBreakdown;
import com.ctps.ctps_api.domain.search.preprocess.SearchTextNormalizer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class UnifiedSearchRankingService {

    private final SearchResultDeduplicator deduplicator;

    public List<UnifiedSearchRankingResult> rank(
            SearchCandidateCollector.CandidateCollection candidates,
            ProcessedSearchQuery processedQuery,
            ProblemSearchSortOption sortOption,
            UserPreferenceProfile preferenceProfile,
            boolean includeScoreBreakdown
    ) {
        RankingContext rankingContext = RankingContext.from(candidates, processedQuery, preferenceProfile);

        Map<String, UnifiedSearchRankingResult> deduplicated = new LinkedHashMap<>();
        for (UnifiedSearchItemResponse item : candidates.allCandidates()) {
            UnifiedSearchRankingResult scored = score(item, rankingContext, includeScoreBreakdown);
            String key = deduplicator.keyOf(scored.getItem());
            UnifiedSearchRankingResult existing = deduplicated.get(key);
            if (existing == null || scored.getTotalScore() > existing.getTotalScore()) {
                deduplicated.put(key, scored);
            }
        }

        return deduplicated.values().stream()
                .sorted(sortComparator(sortOption))
                .toList();
    }

    private UnifiedSearchRankingResult score(
            UnifiedSearchItemResponse item,
            RankingContext context,
            boolean includeScoreBreakdown
    ) {
        MatchContext matchContext = classifyMatch(item, context);
        int baseScore = baseScore(matchContext.rankingType());
        int tagScore = calculateTagScore(item, context);
        int typeScore = calculateTypeScore(item, context);
        int platformScore = calculatePlatformScore(item, context.query(), context.preferenceProfile());
        int difficultyScore = calculateDifficultyScore(item, context.query(), context.preferenceProfile());
        int userPreferenceScore = calculateUserPreferenceScore(item, context.preferenceProfile());
        int freshnessScore = calculateFreshnessScore(item);
        int penaltyScore = calculatePenaltyScore(item, matchContext, tagScore, userPreferenceScore);
        int internalPriorityScore = item.getSource() == SearchItemSource.INTERNAL ? 3 : 0;
        int bookmarkScore = item.isBookmarked() ? 2 : 0;
        int reviewPriorityScore = item.isNeedsReview() ? 2 : 0;
        int ruleScore = baseScore
                + matchContext.matchScore()
                + tagScore
                + typeScore
                + platformScore
                + difficultyScore
                + userPreferenceScore
                + freshnessScore
                + internalPriorityScore
                + bookmarkScore
                + reviewPriorityScore
                - penaltyScore;
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
                        .rankingType(matchContext.rankingType())
                        .matchedKeyword(matchContext.matchedKeyword())
                        .matchedTags(matchContext.matchedTags())
                        .scoreBreakdown(includeScoreBreakdown
                                ? UnifiedSearchScoreBreakdown.builder()
                                .baseScore(baseScore)
                                .matchScore(matchContext.matchScore())
                                .tagScore(tagScore)
                                .typeScore(typeScore)
                                .difficultyScore(difficultyScore)
                                .platformScore(platformScore)
                                .userPreferenceScore(userPreferenceScore)
                                .freshnessScore(freshnessScore)
                                .penaltyScore(penaltyScore)
                                .finalScore(totalScore)
                                .build()
                                : null)
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

    private MatchContext classifyMatch(UnifiedSearchItemResponse item, RankingContext context) {
        ProcessedSearchQuery query = context.query();
        String normalizedKeyword = query.getNormalizedKeyword();
        String normalizedTitle = normalize(item.getTitle());
        String normalizedSummary = normalize(item.getSummary());
        String normalizedDescription = normalize(item.getDescription());
        List<String> normalizedTags = normalizedTags(item);
        SearchCandidateOrigin origin = item.getCandidateOrigin();

        if (origin != SearchCandidateOrigin.FALLBACK_BY_TAG
                && origin != SearchCandidateOrigin.FALLBACK_BY_USER_PREFERENCE
                && StringUtils.hasText(normalizedKeyword)) {
            if (normalizedTitle.equals(normalizedKeyword)) {
                return new MatchContext(SearchRankingType.EXACT, query.getRawKeyword(), matchedTags(normalizedTags, query.getNormalizedTags()), 100);
            }
            if (normalizedTitle.contains(normalizedKeyword)) {
                return new MatchContext(SearchRankingType.EXACT, query.getRawKeyword(), matchedTags(normalizedTags, query.getNormalizedTags()), 90);
            }
            if (normalizedSummary.contains(normalizedKeyword)
                    || normalizedDescription.contains(normalizedKeyword)
                    || normalizedTags.stream().anyMatch(tag -> tag.contains(normalizedKeyword))) {
                return new MatchContext(SearchRankingType.EXACT, query.getRawKeyword(), matchedTags(normalizedTags, query.getNormalizedTags()), 75);
            }
        }

        for (String expandedKeyword : query.getExpandedKeywords()) {
            if (!StringUtils.hasText(expandedKeyword) || expandedKeyword.length() < 2) {
                continue;
            }
            if (normalizedTitle.contains(expandedKeyword)) {
                return new MatchContext(SearchRankingType.PARTIAL, expandedKeyword, matchedTags(normalizedTags, List.of(expandedKeyword)), 55);
            }
            if (normalizedTags.stream().anyMatch(tag -> tag.contains(expandedKeyword))
                    || normalizedSummary.contains(expandedKeyword)
                    || normalizedDescription.contains(expandedKeyword)) {
                return new MatchContext(SearchRankingType.PARTIAL, expandedKeyword, matchedTags(normalizedTags, List.of(expandedKeyword)), 35);
            }
        }

        if (origin == SearchCandidateOrigin.FALLBACK_BY_TAG) {
            return new MatchContext(
                    SearchRankingType.RECOMMENDED_BY_TAG,
                    null,
                    matchedTags(normalizedTags, context.relatedTags()),
                    20
            );
        }

        int relatedTagScore = overlapCount(normalizedTags, contextTags(query.getNormalizedTags()));
        if (relatedTagScore > 0) {
            return new MatchContext(SearchRankingType.RECOMMENDED_BY_TAG, null, matchedTags(normalizedTags, query.getNormalizedTags()), 0);
        }

        return new MatchContext(SearchRankingType.RECOMMENDED_BY_USER_PREFERENCE, null, List.of(), 0);
    }

    private int calculateTagScore(UnifiedSearchItemResponse item, RankingContext context) {
        List<String> normalizedTags = normalizedTags(item);
        if (normalizedTags.isEmpty()) {
            return 0;
        }

        int exactQueryMatches = overlapCount(normalizedTags, context.query().getNormalizedTags());
        int relatedMatches = overlapCount(normalizedTags, context.relatedTags());
        if (exactQueryMatches > 0) {
            return 20;
        }
        if (relatedMatches > 0) {
            return 10;
        }
        return 0;
    }

    private int calculateTypeScore(UnifiedSearchItemResponse item, RankingContext context) {
        List<String> normalizedTags = normalizedTags(item);
        if (normalizedTags.isEmpty()) {
            return 0;
        }
        return overlapCount(normalizedTags, context.relatedTypes()) > 0 ? 15 : 0;
    }

    private int calculatePlatformScore(
            UnifiedSearchItemResponse item,
            ProcessedSearchQuery query,
            UserPreferenceProfile preferenceProfile
    ) {
        String normalizedPlatform = normalize(item.getPlatform());
        if (query.getNormalizedPlatforms().stream().anyMatch(normalizedPlatform::equals)) {
            return 5;
        }
        return preferenceProfile.getPlatformScores().getOrDefault(normalizedPlatform, 0.0) >= 0.6 ? 5 : 0;
    }

    private int calculateDifficultyScore(
            UnifiedSearchItemResponse item,
            ProcessedSearchQuery query,
            UserPreferenceProfile preferenceProfile
    ) {
        if (item.getDifficulty() == null) {
            return 0;
        }
        int queryScore = query.getRequestedDifficulties().stream()
                .mapToInt(requested -> {
                    int distance = Math.abs(difficultyRank(requested) - difficultyRank(item.getDifficulty()));
                    if (distance == 0) return 8;
                    if (distance == 1) return 4;
                    return 0;
                })
                .max()
                .orElse(0);
        int preferenceScore = (int) Math.round(preferenceProfile.getDifficultyScores()
                .getOrDefault(item.getDifficulty().name().toLowerCase(java.util.Locale.ROOT), 0.0) * 8);
        return Math.max(queryScore, preferenceScore);
    }

    private int calculateUserPreferenceScore(UnifiedSearchItemResponse item, UserPreferenceProfile preferenceProfile) {
        List<String> normalizedTags = normalizedTags(item);
        double recentTagWeight = normalizedTags.stream()
                .mapToDouble(tag -> preferenceProfile.getRecentTagScores().getOrDefault(tag, 0.0))
                .max()
                .orElse(0.0);
        double recentTypeWeight = normalizedTags.stream()
                .mapToDouble(tag -> preferenceProfile.getRecentTypeScores().getOrDefault(tag, 0.0))
                .max()
                .orElse(0.0);
        double lifetimeWeight = normalizedTags.stream()
                .mapToDouble(tag -> preferenceProfile.getLifetimeTagScores().getOrDefault(tag, 0.0))
                .max()
                .orElse(0.0);
        double difficultyWeight = item.getDifficulty() == null
                ? 0.0
                : preferenceProfile.getDifficultyScores()
                .getOrDefault(item.getDifficulty().name().toLowerCase(java.util.Locale.ROOT), 0.0);
        double platformWeight = preferenceProfile.getPlatformScores().getOrDefault(normalize(item.getPlatform()), 0.0);

        int tagScore = (int) Math.round(recentTagWeight * 15);
        int typeScore = (int) Math.round(recentTypeWeight * 12);
        int difficultyScore = (int) Math.round(difficultyWeight * 8);
        int platformScore = (int) Math.round(platformWeight * 5);
        int lifetimeScore = (int) Math.round(lifetimeWeight * 4);
        return tagScore + typeScore + difficultyScore + platformScore + lifetimeScore;
    }

    private int calculateFreshnessScore(UnifiedSearchItemResponse item) {
        if (item.getCreatedAt() == null) {
            return 0;
        }
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        if (item.getCreatedAt().isAfter(thirtyDaysAgo)) {
            return 5;
        }
        if (item.getCreatedAt().isAfter(LocalDateTime.now().minusDays(90))) {
            return 3;
        }
        return 0;
    }

    private int calculatePenaltyScore(
            UnifiedSearchItemResponse item,
            MatchContext matchContext,
            int tagScore,
            int userPreferenceScore
    ) {
        int penalty = 0;
        if (matchContext.rankingType() == SearchRankingType.RECOMMENDED_BY_USER_PREFERENCE && tagScore == 0) {
            penalty += 20;
        }
        if (item.isSolved() && matchContext.rankingType() != SearchRankingType.EXACT) {
            penalty += 5;
        }
        if (userPreferenceScore == 0 && matchContext.rankingType() == SearchRankingType.RECOMMENDED_BY_USER_PREFERENCE) {
            penalty += 10;
        }
        return penalty;
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
        return SearchTextNormalizer.normalize(value);
    }

    private List<String> normalizedTags(UnifiedSearchItemResponse item) {
        if (item.getTags() == null) {
            return List.of();
        }
        return item.getTags().stream()
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> matchedTags(List<String> normalizedTags, List<String> candidateTags) {
        if (normalizedTags.isEmpty() || candidateTags.isEmpty()) {
            return List.of();
        }
        return normalizedTags.stream()
                .filter(tag -> candidateTags.stream().anyMatch(tag::contains))
                .distinct()
                .toList();
    }

    private int overlapCount(List<String> normalizedTags, List<String> targets) {
        if (normalizedTags.isEmpty() || targets.isEmpty()) {
            return 0;
        }
        return (int) normalizedTags.stream()
                .filter(tag -> targets.stream().anyMatch(target -> tag.contains(target) || target.contains(tag)))
                .distinct()
                .count();
    }

    private List<String> contextTags(List<String> normalizedTags) {
        return normalizedTags == null ? List.of() : normalizedTags;
    }

    private int baseScore(SearchRankingType rankingType) {
        return switch (rankingType) {
            case EXACT -> 1000;
            case PARTIAL -> 700;
            case RECOMMENDED_BY_TAG -> 400;
            case RECOMMENDED_BY_USER_PREFERENCE -> 350;
        };
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    private record MatchContext(
            SearchRankingType rankingType,
            String matchedKeyword,
            List<String> matchedTags,
            int matchScore
    ) {
    }

    private record RankingContext(
            ProcessedSearchQuery query,
            UserPreferenceProfile preferenceProfile,
            List<String> relatedTags,
            List<String> relatedTypes
    ) {
        private static RankingContext from(
                SearchCandidateCollector.CandidateCollection candidates,
                ProcessedSearchQuery query,
                UserPreferenceProfile preferenceProfile
        ) {
            List<String> relatedSignals = java.util.stream.Stream.concat(
                            candidates.getExactCandidates().stream(),
                            candidates.getPartialCandidates().stream())
                    .flatMap(item -> item.getTags() == null ? java.util.stream.Stream.empty() : item.getTags().stream())
                    .map(SearchTextNormalizer::normalize)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .limit(6)
                    .toList();
            return new RankingContext(query, preferenceProfile, relatedSignals, relatedSignals);
        }
    }
}
