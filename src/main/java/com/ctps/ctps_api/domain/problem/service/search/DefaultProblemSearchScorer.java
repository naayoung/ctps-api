package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.search.preprocess.SearchTextNormalizer;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class DefaultProblemSearchScorer implements ProblemSearchScorer {

    private static final int PROVIDER_SCORE_WEIGHT = 4;

    private final ProblemPersonalizationScorer personalizationScorer;

    @Override
    public ProblemSearchScore score(
            ProcessedSearchQuery query,
            ExternalProblemSearchItemResponse item,
            ProviderScoreSignal providerScoreSignal
    ) {
        int keywordScore = calculateKeywordScore(query, item);
        int tagScore = calculateTagScore(query.getNormalizedTags(), item.getTags());
        int difficultyScore = calculateDifficultyScore(query.getRequestedDifficulties(), item.getDifficulty());
        int unsolvedBonus = calculateUnsolvedBonus(item);
        int platformScore = calculatePlatformScore(query.getNormalizedPlatforms(), item.getPlatform());
        int personalizationScore = personalizationScorer.score(query, item);
        int ruleScore = keywordScore + tagScore + difficultyScore + unsolvedBonus + platformScore + personalizationScore;
        double providerNormalizedScore = providerScoreSignal == null || providerScoreSignal.getNormalizedScore() == null
                ? 0.0
                : providerScoreSignal.getNormalizedScore();
        int providerWeightedScore = (int) Math.round(providerNormalizedScore * PROVIDER_SCORE_WEIGHT);

        return ProblemSearchScore.builder()
                .ruleScore(ruleScore)
                .keywordScore(keywordScore)
                .tagScore(tagScore)
                .difficultyScore(difficultyScore)
                .unsolvedBonus(unsolvedBonus)
                .platformScore(platformScore)
                .personalizationScore(personalizationScore)
                .providerWeightedScore(providerWeightedScore)
                .providerRawScore(providerScoreSignal == null ? null : providerScoreSignal.getRawScore())
                .providerNormalizedScore(providerNormalizedScore)
                .totalScore(ruleScore + providerWeightedScore)
                .build();
    }

    private int calculateKeywordScore(ProcessedSearchQuery query, ExternalProblemSearchItemResponse item) {
        if (!StringUtils.hasText(query.getNormalizedKeyword())) {
            return 0;
        }

        String normalizedTitle = normalizeText(item.getTitle());
        if (normalizedTitle.contains(query.getNormalizedKeyword())) {
            return 10;
        }

        boolean titleTokenMatch = query.getKeywordTokens().stream()
                .anyMatch(token -> normalizedTitle.contains(token));
        if (titleTokenMatch) {
            return 7;
        }

        String tagAndDescriptionText = normalizeText(String.join(" ", safeTags(item.getTags())))
                + " " + normalizeText(item.getRecommendationReason());
        boolean metadataMatch = query.getKeywordTokens().stream()
                .anyMatch(token -> tagAndDescriptionText.contains(token));
        return metadataMatch ? 4 : 0;
    }

    private int calculateTagScore(List<String> normalizedRequestedTags, List<String> itemTags) {
        if (normalizedRequestedTags == null || normalizedRequestedTags.isEmpty()) {
            return 0;
        }

        Set<String> normalizedItemTags = safeTags(itemTags).stream()
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        long matches = normalizedRequestedTags.stream()
                .filter(normalizedTag -> normalizedItemTags.stream().anyMatch(itemTag -> itemTag.contains(normalizedTag)))
                .count();

        if (matches >= 2) {
            return 6;
        }
        if (matches == 1) {
            return 3;
        }
        return 0;
    }

    private int calculateDifficultyScore(List<Problem.Difficulty> requestedDifficulties, Problem.Difficulty itemDifficulty) {
        if (requestedDifficulties == null || requestedDifficulties.isEmpty() || itemDifficulty == null) {
            return 0;
        }

        return requestedDifficulties.stream()
                .filter(Objects::nonNull)
                .mapToInt(requestedDifficulty -> {
                    int distance = Math.abs(difficultyRank(requestedDifficulty) - difficultyRank(itemDifficulty));
                    if (distance == 0) {
                        return 4;
                    }
                    if (distance == 1) {
                        return 2;
                    }
                    return 0;
                })
                .max()
                .orElse(0);
    }

    private int calculateUnsolvedBonus(ExternalProblemSearchItemResponse item) {
        // External results do not currently have reliable user solve-state mapping.
        // Keep this hook for future personalization or internal/external mapping.
        return 0;
    }

    private int calculatePlatformScore(List<String> normalizedRequestedPlatforms, String itemPlatform) {
        if (normalizedRequestedPlatforms == null || normalizedRequestedPlatforms.isEmpty() || !StringUtils.hasText(itemPlatform)) {
            return 0;
        }

        String normalizedPlatform = normalizeText(itemPlatform);
        boolean matched = normalizedRequestedPlatforms.stream()
                .anyMatch(normalizedPlatform::equals);
        return matched ? 2 : 0;
    }

    private int difficultyRank(Problem.Difficulty difficulty) {
        return switch (difficulty) {
            case easy -> 1;
            case medium -> 2;
            case hard -> 3;
        };
    }

    private String normalizeText(String text) {
        return SearchTextNormalizer.normalize(text);
    }

    private List<String> safeTags(List<String> tags) {
        return tags == null ? List.of() : tags;
    }
}
