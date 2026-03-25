package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.service.search.ExternalProblemProviderScoreNormalizer;
import com.ctps.ctps_api.domain.problem.service.search.ExternalProblemProviderScoreResolver;
import com.ctps.ctps_api.domain.problem.service.search.ProcessedSearchQuery;
import com.ctps.ctps_api.domain.problem.service.search.ProblemSearchScore;
import com.ctps.ctps_api.domain.problem.service.search.ProblemSearchScorer;
import com.ctps.ctps_api.domain.problem.service.search.ProviderScoreSignal;
import com.ctps.ctps_api.domain.problem.service.search.ProviderScoredExternalProblem;
import com.ctps.ctps_api.domain.problem.service.search.SearchQueryPreprocessor;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalProblemSearchService {

    private final List<ExternalProblemProvider> providers;
    private final ExternalProblemCacheService cacheService;
    private final ExternalSearchMetricsService metricsService;
    private final ExternalProblemQueryKeyGenerator queryKeyGenerator;
    private final ProblemSearchScorer problemSearchScorer;
    private final SearchQueryPreprocessor searchQueryPreprocessor;
    private final List<ExternalProblemProviderScoreResolver> providerScoreResolvers;
    private final ExternalProblemProviderScoreNormalizer providerScoreNormalizer;

    public ExternalProblemSearchResponse search(ProblemSearchRequest request) {
        Pageable pageable = request.toPageable();
        ProcessedSearchQuery processedQuery = searchQueryPreprocessor.process(request);
        List<ProviderScoredExternalProblem> fetchedItems = fetchProviderResults(request, processedQuery);
        List<ProviderScoredExternalProblem> rankedItems = deduplicateAndScore(fetchedItems, processedQuery);
        List<ProviderScoredExternalProblem> sortedItems = sortResults(rankedItems);
        return toPageResponse(sortedItems, pageable);
    }

    private List<ProviderScoredExternalProblem> fetchProviderResults(
            ProblemSearchRequest request,
            ProcessedSearchQuery processedQuery
    ) {
        List<ProviderScoredExternalProblem> merged = new java.util.ArrayList<>();
        for (ExternalProblemProvider provider : providers) {
            merged.addAll(getCachedOrSearch(provider, request, processedQuery));
        }
        return merged;
    }

    private List<ProviderScoredExternalProblem> getCachedOrSearch(
            ExternalProblemProvider provider,
            ProblemSearchRequest request,
            ProcessedSearchQuery processedQuery
    ) {
        String providerName = provider.getClass().getSimpleName();
        String queryKey = queryKeyGenerator.generate(providerName, request);
        ExternalProblemCacheService.CachedExternalProblemResult cached = cacheService.findValidCache(providerName, queryKey);

        if (cached != null) {
            metricsService.recordCacheHit();
            return attachProviderSignals(providerName, cached.items(), processedQuery);
        }

        metricsService.recordCacheMiss();
        try {
            List<ExternalProblemSearchItemResponse> providerItems = provider.search(request);
            cacheService.save(providerName, queryKey, providerItems, providerItems.size(), providerItems.isEmpty() ? 0 : 1);
            metricsService.recordProviderSuccess(providerName);
            return attachProviderSignals(providerName, providerItems, processedQuery);
        } catch (Exception exception) {
            metricsService.recordProviderFailure(providerName);
            log.warn(
                    "external provider search failed providerName={} queryKey={} message={}",
                    providerName,
                    queryKey,
                    exception.getMessage(),
                    exception
            );
            return List.of();
        }
    }

    private List<ProviderScoredExternalProblem> attachProviderSignals(
            String providerName,
            List<ExternalProblemSearchItemResponse> items,
            ProcessedSearchQuery processedQuery
    ) {
        List<ProviderScoreSignal> rawSignals = resolveProviderSignals(providerName, processedQuery, items);
        List<ProviderScoreSignal> normalizedSignals = providerScoreNormalizer.normalize(rawSignals);

        List<ProviderScoredExternalProblem> results = new java.util.ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ProviderScoreSignal signal = index < normalizedSignals.size() ? normalizedSignals.get(index) : defaultSignal(providerName);
            results.add(ProviderScoredExternalProblem.builder()
                    .item(items.get(index))
                    .providerScoreSignal(signal)
                    .build());
        }
        return results;
    }

    private List<ProviderScoreSignal> resolveProviderSignals(
            String providerName,
            ProcessedSearchQuery processedQuery,
            List<ExternalProblemSearchItemResponse> items
    ) {
        return providerScoreResolvers.stream()
                .filter(resolver -> resolver.supports(providerName))
                .findFirst()
                .orElseThrow()
                .resolve(providerName, processedQuery, items);
    }

    private ProviderScoreSignal defaultSignal(String providerName) {
        return ProviderScoreSignal.builder()
                .providerName(providerName)
                .build();
    }

    private List<ProviderScoredExternalProblem> deduplicateAndScore(
            List<ProviderScoredExternalProblem> items,
            ProcessedSearchQuery processedQuery
    ) {
        Map<String, ProviderScoredExternalProblem> deduplicated = new LinkedHashMap<>();
        for (ProviderScoredExternalProblem item : items) {
            ProviderScoredExternalProblem scoredItem = applyScore(item, processedQuery);
            String deduplicationKey = buildDeduplicationKey(scoredItem.getItem());
            ProviderScoredExternalProblem existing = deduplicated.get(deduplicationKey);
            if (existing == null || isPreferred(scoredItem, existing)) {
                deduplicated.put(deduplicationKey, scoredItem);
            }
        }
        return List.copyOf(deduplicated.values());
    }

    private List<ProviderScoredExternalProblem> sortResults(List<ProviderScoredExternalProblem> items) {
        return items.stream()
                .sorted(Comparator
                        .comparingInt(this::relevanceScoreOrZero).reversed()
                        .thenComparing(candidate -> candidate.getItem().isSolved())
                        .thenComparing(this::problemNumberSortValue)
                        .thenComparing(candidate -> safeLower(candidate.getItem().getPlatform()))
                        .thenComparing(candidate -> safeLower(candidate.getItem().getTitle())))
                .toList();
    }

    private ExternalProblemSearchResponse toPageResponse(
            List<ProviderScoredExternalProblem> items,
            Pageable pageable
    ) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), items.size());
        List<ExternalProblemSearchItemResponse> pageContent =
                start >= items.size() ? List.of() : items.subList(start, end).stream().map(ProviderScoredExternalProblem::getItem).toList();

        Page<ExternalProblemSearchItemResponse> page = new PageImpl<>(pageContent, pageable, items.size());
        return ExternalProblemSearchResponse.from(page);
    }

    private ProviderScoredExternalProblem applyScore(
            ProviderScoredExternalProblem candidate,
            ProcessedSearchQuery processedQuery
    ) {
        ProblemSearchScore score = problemSearchScorer.score(processedQuery, candidate.getItem(), candidate.getProviderScoreSignal());
        return candidate.toBuilder()
                .searchScore(score)
                .item(candidate.getItem().toBuilder()
                        .relevanceScore(score.getTotalScore())
                        .build())
                .build();
    }

    private String buildDeduplicationKey(ExternalProblemSearchItemResponse item) {
        return safeLower(item.getPlatform()) + "|" + safeLower(item.getProblemNumber());
    }

    private boolean isPreferred(
            ProviderScoredExternalProblem candidate,
            ProviderScoredExternalProblem existing
    ) {
        int scoreCompare = Integer.compare(relevanceScoreOrZero(candidate), relevanceScoreOrZero(existing));
        if (scoreCompare != 0) {
            return scoreCompare > 0;
        }

        int richnessCompare = Integer.compare(informationRichness(candidate.getItem()), informationRichness(existing.getItem()));
        if (richnessCompare != 0) {
            return richnessCompare > 0;
        }

        return false;
    }

    private int informationRichness(ExternalProblemSearchItemResponse item) {
        int richness = 0;
        richness += item.getTags() == null ? 0 : item.getTags().size();
        richness += hasText(item.getExternalUrl()) ? 1 : 0;
        richness += hasText(item.getRecommendationReason()) ? 1 : 0;
        richness += item.getDifficulty() == null ? 0 : 1;
        return richness;
    }

    private int relevanceScoreOrZero(ProviderScoredExternalProblem candidate) {
        return candidate.getItem().getRelevanceScore() == null ? 0 : candidate.getItem().getRelevanceScore();
    }

    private int problemNumberSortValue(ProviderScoredExternalProblem candidate) {
        try {
            return Integer.parseInt(candidate.getItem().getProblemNumber());
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
