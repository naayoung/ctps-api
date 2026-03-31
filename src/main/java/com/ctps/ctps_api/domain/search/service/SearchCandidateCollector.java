package com.ctps.ctps_api.domain.search.service;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.ProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.search.ProcessedSearchQuery;
import com.ctps.ctps_api.domain.search.dto.SearchCandidateOrigin;
import com.ctps.ctps_api.domain.search.dto.UnifiedSearchItemResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SearchCandidateCollector {

    private final ProblemSearchService problemSearchService;
    private final ExternalProblemSearchService externalProblemSearchService;
    private final UnifiedSearchResponseMapper responseMapper;
    private final SearchResultDeduplicator deduplicator;
    private final SearchTypeCanonicalizer searchTypeCanonicalizer;

    public CandidateCollection collect(
            ProblemSearchRequest request,
            ProcessedSearchQuery processedQuery,
            UserPreferenceProfile preferenceProfile,
            int aggregateSize,
            int targetSize
    ) {
        List<UnifiedSearchItemResponse> exactCandidates = searchAll(buildRequest(request, request.getKeyword(), request.getTags(),
                request.getPlatform(), request.getDifficulty(), aggregateSize), SearchCandidateOrigin.EXACT);

        List<UnifiedSearchItemResponse> partialCandidates = List.of();
        List<UnifiedSearchItemResponse> fallbackCandidates = List.of();

        int exactDistinctCount = deduplicator.countDistinct(exactCandidates);
        if (exactDistinctCount < targetSize && !processedQuery.getExpandedKeywords().isEmpty()) {
            List<UnifiedSearchItemResponse> expanded = new ArrayList<>();
            for (String expandedKeyword : processedQuery.getExpandedKeywords()) {
                expanded.addAll(searchAll(buildRequest(
                        request,
                        expandedKeyword,
                        request.getTags(),
                        request.getPlatform(),
                        request.getDifficulty(),
                        aggregateSize
                ), SearchCandidateOrigin.PARTIAL));
            }
            partialCandidates = deduplicator.deduplicate(expanded);
        }

        int combinedDistinctCount = deduplicator.countDistinct(merge(exactCandidates, partialCandidates));
        if (combinedDistinctCount < targetSize) {
            RelatedPreferenceSeed relatedSeed = deriveRelatedSeed(exactCandidates, partialCandidates, preferenceProfile);
            List<UnifiedSearchItemResponse> fallback = new ArrayList<>();

            if (!relatedSeed.relatedTags().isEmpty() || !relatedSeed.platforms().isEmpty() || !relatedSeed.difficulties().isEmpty()) {
                fallback.addAll(searchAll(buildRequest(
                        request,
                        null,
                        relatedSeed.relatedTags(),
                        relatedSeed.platforms(),
                        relatedSeed.difficulties(),
                        aggregateSize
                ), SearchCandidateOrigin.FALLBACK_BY_TAG));
                for (String relatedTag : relatedSeed.relatedTags()) {
                    fallback.addAll(searchAll(buildRequest(
                            request,
                            null,
                            List.of(relatedTag),
                            relatedSeed.platforms(),
                            relatedSeed.difficulties(),
                            aggregateSize
                    ), SearchCandidateOrigin.FALLBACK_BY_TAG));
                }
            }

            if (!relatedSeed.preferenceTags().isEmpty() || !relatedSeed.platforms().isEmpty() || !relatedSeed.difficulties().isEmpty()) {
                fallback.addAll(searchAll(buildRequest(
                        request,
                        null,
                        relatedSeed.preferenceTags(),
                        relatedSeed.platforms(),
                        relatedSeed.difficulties(),
                        aggregateSize
                ), SearchCandidateOrigin.FALLBACK_BY_USER_PREFERENCE));
                for (String preferenceTag : relatedSeed.preferenceTags()) {
                    fallback.addAll(searchAll(buildRequest(
                            request,
                            null,
                            List.of(preferenceTag),
                            relatedSeed.platforms(),
                            relatedSeed.difficulties(),
                            aggregateSize
                    ), SearchCandidateOrigin.FALLBACK_BY_USER_PREFERENCE));
                }
            }

            fallbackCandidates = deduplicator.deduplicate(fallback);
        }

        return CandidateCollection.builder()
                .exactCandidates(deduplicator.deduplicate(exactCandidates))
                .partialCandidates(partialCandidates)
                .fallbackCandidates(fallbackCandidates)
                .build();
    }

    private List<UnifiedSearchItemResponse> searchAll(
            ProblemSearchRequest request,
            SearchCandidateOrigin origin
    ) {
        ProblemSearchResponse internalResponse = problemSearchService.search(request);
        ExternalProblemSearchResponse externalResponse = externalProblemSearchService.search(request);

        List<UnifiedSearchItemResponse> items = new ArrayList<>();
        internalResponse.getContent().stream()
                .map(responseMapper::fromInternal)
                .map(item -> item.toBuilder().candidateOrigin(origin).build())
                .forEach(items::add);
        externalResponse.getContent().stream()
                .map(responseMapper::fromExternal)
                .map(item -> item.toBuilder().candidateOrigin(origin).build())
                .forEach(items::add);
        return items;
    }

    private ProblemSearchRequest buildRequest(
            ProblemSearchRequest original,
            String keyword,
            List<String> tags,
            List<String> platforms,
            List<Problem.Difficulty> difficulties,
            int size
    ) {
        ProblemSearchRequest request = new ProblemSearchRequest();
        request.setKeyword(keyword);
        request.setTags(tags);
        request.setPlatform(platforms);
        request.setDifficulty(difficulties);
        request.setResult(original.getResult());
        request.setNeedsReview(original.getNeedsReview());
        request.setBookmarked(original.getBookmarked());
        request.setSort(original.getSort());
        request.setPage(0);
        request.setSize(size);
        return request;
    }

    private List<UnifiedSearchItemResponse> merge(
            List<UnifiedSearchItemResponse> first,
            List<UnifiedSearchItemResponse> second
    ) {
        List<UnifiedSearchItemResponse> merged = new ArrayList<>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return merged;
    }

    private RelatedPreferenceSeed deriveRelatedSeed(
            List<UnifiedSearchItemResponse> exactCandidates,
            List<UnifiedSearchItemResponse> partialCandidates,
            UserPreferenceProfile preferenceProfile
    ) {
        List<UnifiedSearchItemResponse> seedItems = deduplicator.deduplicate(merge(exactCandidates, partialCandidates));

        Set<String> relatedTags = new LinkedHashSet<>();
        Set<String> platforms = new LinkedHashSet<>();
        Set<Problem.Difficulty> difficulties = new LinkedHashSet<>();
        Map<String, Integer> tagFrequency = new LinkedHashMap<>();

        for (UnifiedSearchItemResponse item : seedItems.stream().limit(8).toList()) {
            if (item.getTags() != null) {
                item.getTags().stream()
                        .filter(StringUtils::hasText)
                        .map(searchTypeCanonicalizer::canonicalizeTag)
                        .map(value -> value.trim().toLowerCase(Locale.ROOT))
                        .forEach(tag -> tagFrequency.merge(tag, 1, Integer::sum));
            }
            if (StringUtils.hasText(item.getPlatform())) {
                platforms.add(item.getPlatform());
            }
            if (item.getDifficulty() != null) {
                difficulties.add(item.getDifficulty());
            }
        }

        tagFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .limit(4)
                .forEach(relatedTags::add);

        List<String> preferenceTags = new ArrayList<>();
        preferenceTags.addAll(preferenceProfile.topTagKeys(3));
        for (String typeKey : preferenceProfile.topTypeKeys(2)) {
            if (!preferenceTags.contains(typeKey)) {
                preferenceTags.add(typeKey);
            }
        }

        if (platforms.isEmpty() && StringUtils.hasText(preferenceProfile.topPlatform())) {
            platforms.add(preferenceProfile.topPlatform());
        }
        if (difficulties.isEmpty() && preferenceProfile.topDifficulty() != null) {
            difficulties.add(preferenceProfile.topDifficulty());
        }

        return new RelatedPreferenceSeed(
                List.copyOf(relatedTags.stream().map(searchTypeCanonicalizer::canonicalizeTag).distinct().limit(4).toList()),
                List.copyOf(preferenceTags.stream().map(searchTypeCanonicalizer::canonicalizeTag).distinct().limit(4).toList()),
                List.copyOf(platforms.stream().limit(2).toList()),
                List.copyOf(difficulties.stream().limit(2).toList())
        );
    }

    private record RelatedPreferenceSeed(
            List<String> relatedTags,
            List<String> preferenceTags,
            List<String> platforms,
            List<Problem.Difficulty> difficulties
    ) {
    }

    @Getter
    @Builder
    public static class CandidateCollection {
        private List<UnifiedSearchItemResponse> exactCandidates;
        private List<UnifiedSearchItemResponse> partialCandidates;
        private List<UnifiedSearchItemResponse> fallbackCandidates;

        public List<UnifiedSearchItemResponse> allCandidates() {
            List<UnifiedSearchItemResponse> merged = new ArrayList<>();
            merged.addAll(exactCandidates);
            merged.addAll(partialCandidates);
            merged.addAll(fallbackCandidates);
            return List.copyOf(merged);
        }
    }
}
