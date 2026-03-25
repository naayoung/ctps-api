package com.ctps.ctps_api.domain.search.service;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.search.dto.SearchItemSource;
import com.ctps.ctps_api.domain.search.dto.UnifiedSearchItemResponse;
import org.springframework.stereotype.Component;

@Component
public class UnifiedSearchResponseMapper {

    public UnifiedSearchItemResponse fromInternal(ProblemSearchItemResponse item) {
        return UnifiedSearchItemResponse.builder()
                .id(item.getId())
                .source(SearchItemSource.INTERNAL)
                .sourceLabel("내 기록")
                .title(item.getTitle())
                .platform(item.getPlatform())
                .problemNumber(item.getProblemNumber())
                .difficulty(item.getDifficulty())
                .difficultyLabel(toDifficultyLabel(item.getDifficulty()))
                .tags(item.getTags())
                .summary(item.getMemoSummary())
                .description(item.getMemoSummary())
                .result(item.getResult())
                .needsReview(item.isNeedsReview())
                .bookmarked(item.isBookmarked())
                .solved(item.getResult() == Problem.Result.success)
                .lastSolvedAt(item.getLastSolvedAt())
                .createdAt(item.getCreatedAt())
                .build();
    }

    public UnifiedSearchItemResponse fromExternal(ExternalProblemSearchItemResponse item) {
        return UnifiedSearchItemResponse.builder()
                .id(item.getId())
                .source(SearchItemSource.EXTERNAL)
                .sourceLabel("외부 추천")
                .providerKey(item.getProviderKey())
                .providerLabel(item.getProviderLabel())
                .title(item.getTitle())
                .platform(item.getPlatform())
                .problemNumber(item.getProblemNumber())
                .difficulty(item.getDifficulty())
                .difficultyLabel(toDifficultyLabel(item.getDifficulty()))
                .tags(item.getTags())
                .summary(item.getSummary())
                .description(item.getRecommendationReason())
                .externalUrl(item.getExternalUrl())
                .bookmarked(false)
                .needsReview(false)
                .solved(item.isSolved())
                .providerScore(item.getProviderScore())
                .providerNormalizedScore(item.getProviderNormalizedScore())
                .build();
    }

    private String toDifficultyLabel(Problem.Difficulty difficulty) {
        if (difficulty == null) {
            return "";
        }
        return switch (difficulty) {
            case easy -> "Easy";
            case medium -> "Medium";
            case hard -> "Hard";
        };
    }
}
