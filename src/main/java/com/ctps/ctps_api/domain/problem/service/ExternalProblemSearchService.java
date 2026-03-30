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
        ExternalFetchOutcome fetchOutcome = fetchProviderResults(request, processedQuery);
        List<ProviderScoredExternalProblem> rankedItems = deduplicateAndScore(fetchOutcome.items(), processedQuery);
        List<ProviderScoredExternalProblem> sortedItems = sortResults(rankedItems, request);
        return toPageResponse(sortedItems, pageable, fetchOutcome.failedProviders());
    }

    private ExternalFetchOutcome fetchProviderResults(
            ProblemSearchRequest request,
            ProcessedSearchQuery processedQuery
    ) {
        List<ProviderScoredExternalProblem> merged = new java.util.ArrayList<>();
        List<String> failedProviders = new java.util.ArrayList<>();
        for (ExternalProblemProvider provider : providers) {
            ProviderFetchResult result = getCachedOrSearch(provider, request, processedQuery);
            merged.addAll(result.items());
            if (result.failedProvider() != null) {
                failedProviders.add(result.failedProvider());
            }
        }
        return new ExternalFetchOutcome(List.copyOf(merged), List.copyOf(failedProviders));
    }

    private ProviderFetchResult getCachedOrSearch(
            ExternalProblemProvider provider,
            ProblemSearchRequest request,
            ProcessedSearchQuery processedQuery
    ) {
        String providerName = resolveProviderKey(provider);
        String queryKey = queryKeyGenerator.generate(providerName, request);
        ExternalProblemCacheService.CachedExternalProblemResult cached = cacheService.findValidCache(providerName, queryKey);

        if (cached != null) {
            metricsService.recordCacheHit();
            return new ProviderFetchResult(
                    attachProviderSignals(provider, cached.items(), processedQuery),
                    null
            );
        }

        metricsService.recordCacheMiss();
        try {
            List<ExternalProblemSearchItemResponse> providerItems = provider.search(request);
            cacheService.save(providerName, queryKey, providerItems, providerItems.size(), providerItems.isEmpty() ? 0 : 1);
            metricsService.recordProviderSuccess(providerName);
            return new ProviderFetchResult(
                    attachProviderSignals(provider, providerItems, processedQuery),
                    null
            );
        } catch (Exception exception) {
            metricsService.recordProviderFailure(providerName);
            log.warn(
                    "external provider search failed providerName={} queryKey={} message={}",
                    providerName,
                    queryKey,
                    exception.getMessage(),
                    exception
            );
            return new ProviderFetchResult(List.of(), resolveProviderLabel(provider, providerName));
        }
    }

    private List<ProviderScoredExternalProblem> attachProviderSignals(
            ExternalProblemProvider provider,
            List<ExternalProblemSearchItemResponse> items,
            ProcessedSearchQuery processedQuery
    ) {
        String providerName = resolveProviderKey(provider);
        List<ProviderScoreSignal> rawSignals = resolveProviderSignals(providerName, processedQuery, items);
        List<ProviderScoreSignal> normalizedSignals = providerScoreNormalizer.normalize(rawSignals);

        List<ProviderScoredExternalProblem> results = new java.util.ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ProviderScoreSignal signal = index < normalizedSignals.size() ? normalizedSignals.get(index) : defaultSignal(providerName);
            results.add(ProviderScoredExternalProblem.builder()
                    .item(items.get(index).toBuilder()
                            .providerKey(providerName)
                            .providerLabel(resolveProviderLabel(provider, providerName))
                            .providerScore(signal.getRawScore())
                            .providerNormalizedScore(signal.getNormalizedScore())
                            .build())
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

    private String resolveProviderKey(ExternalProblemProvider provider) {
        String candidate = provider.providerKey();
        return hasText(candidate) ? candidate : provider.getClass().getSimpleName();
    }

    private String resolveProviderLabel(ExternalProblemProvider provider, String providerName) {
        String candidate = provider.providerLabel();
        return hasText(candidate) ? candidate : providerName;
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

    private List<ProviderScoredExternalProblem> sortResults(
            List<ProviderScoredExternalProblem> items,
            ProblemSearchRequest request
    ) {
        List<ProviderScoredExternalProblem> sorted = items.stream()
                .sorted(Comparator
                        .comparingInt(this::relevanceScoreOrZero).reversed()
                        .thenComparing(candidate -> candidate.getItem().isSolved())
                        .thenComparing(this::problemNumberSortValue)
                        .thenComparing(candidate -> safeLower(candidate.getItem().getPlatform()))
                        .thenComparing(candidate -> safeLower(candidate.getItem().getTitle())))
                .toList();
        return diversifyProvidersIfNeeded(sorted, request);
    }

    private List<ProviderScoredExternalProblem> diversifyProvidersIfNeeded(
            List<ProviderScoredExternalProblem> sorted,
            ProblemSearchRequest request
    ) {
        if (sorted.size() < 2
                || !request.getPlatform().isEmpty()
                || request.getSortOption() != com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchSortOption.RELEVANCE) {
            return sorted;
        }

        Map<String, java.util.ArrayDeque<ProviderScoredExternalProblem>> grouped = new LinkedHashMap<>();
        for (ProviderScoredExternalProblem item : sorted) {
            String providerKey = safeLower(item.getItem().getProviderKey());
            grouped.computeIfAbsent(providerKey, key -> new java.util.ArrayDeque<>()).add(item);
        }

        if (grouped.size() < 2) {
            return sorted;
        }

        int diversifiedWindow = Math.min(sorted.size(), Math.max(request.getSize(), 9));
        List<ProviderScoredExternalProblem> diversified = new java.util.ArrayList<>(sorted.size());

        while (diversified.size() < diversifiedWindow) {
            boolean progressed = false;
            for (java.util.ArrayDeque<ProviderScoredExternalProblem> queue : grouped.values()) {
                ProviderScoredExternalProblem next = queue.pollFirst();
                if (next == null) {
                    continue;
                }
                diversified.add(next);
                progressed = true;
                if (diversified.size() >= diversifiedWindow) {
                    break;
                }
            }
            if (!progressed) {
                break;
            }
        }

        for (ProviderScoredExternalProblem item : sorted) {
            if (!diversified.contains(item)) {
                diversified.add(item);
            }
        }

        return List.copyOf(diversified);
    }

    private ExternalProblemSearchResponse toPageResponse(
            List<ProviderScoredExternalProblem> items,
            Pageable pageable,
            List<String> failedProviders
    ) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), items.size());
        List<ExternalProblemSearchItemResponse> pageContent =
                start >= items.size() ? List.of() : items.subList(start, end).stream().map(ProviderScoredExternalProblem::getItem).toList();

        Page<ExternalProblemSearchItemResponse> page = new PageImpl<>(pageContent, pageable, items.size());
        return ExternalProblemSearchResponse.builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .failedProviders(failedProviders)
                .warningMessage(buildWarningMessage(failedProviders))
                .build();
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

    private String buildWarningMessage(List<String> failedProviders) {
        if (failedProviders.isEmpty()) {
            return null;
        }
        return "일부 외부 검색 제공자 응답이 지연되거나 실패해 결과가 일부만 표시될 수 있습니다.";
    }

    private record ProviderFetchResult(
            List<ProviderScoredExternalProblem> items,
            String failedProvider
    ) {
    }

    private record ExternalFetchOutcome(
            List<ProviderScoredExternalProblem> items,
            List<String> failedProviders
    ) {
    }
}
