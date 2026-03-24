package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalProblemSearchService {

    private final List<ExternalProblemProvider> providers;
    private final ExternalProblemCacheService cacheService;
    private final ExternalSearchMetricsService metricsService;

    public ExternalProblemSearchResponse search(ProblemSearchRequest request) {
        Pageable pageable = request.toPageable();

        List<ExternalProblemSearchItemResponse> merged = new ArrayList<>();
        for (ExternalProblemProvider provider : providers) {
            String providerName = provider.getClass().getSimpleName();
            String queryKey = buildQueryKey(providerName, request);
            ExternalProblemCacheService.CachedExternalProblemResult cached =
                    cacheService.findValidCache(providerName, queryKey);

            if (cached != null) {
                metricsService.recordCacheHit();
                merged.addAll(cached.items());
                continue;
            }

            metricsService.recordCacheMiss();
            try {
                List<ExternalProblemSearchItemResponse> providerItems = provider.search(request);
                cacheService.save(providerName, queryKey, providerItems, providerItems.size(), providerItems.isEmpty() ? 0 : 1);
                metricsService.recordProviderSuccess(providerName);
                merged.addAll(providerItems);
            } catch (Exception exception) {
                metricsService.recordProviderFailure(providerName);
            }
        }

        merged = merged.stream()
                .sorted((left, right) -> {
                    int reason = left.getRecommendationReason().compareTo(right.getRecommendationReason());
                    if (reason != 0) return reason;
                    return left.getProblemNumber().compareTo(right.getProblemNumber());
                })
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), merged.size());
        List<ExternalProblemSearchItemResponse> pageContent =
                start >= merged.size() ? List.of() : merged.subList(start, end);

        Page<ExternalProblemSearchItemResponse> page = new PageImpl<>(pageContent, pageable, merged.size());
        return ExternalProblemSearchResponse.from(page);
    }

    private String buildQueryKey(String providerName, ProblemSearchRequest request) {
        return providerName
                + "|keyword=" + request.getKeyword()
                + "|platform=" + request.getPlatform()
                + "|difficulty=" + request.getDifficulty()
                + "|tags=" + request.getTags()
                + "|sort=" + request.getSort()
                + "|page=" + request.getPage()
                + "|size=" + request.getSize();
    }
}
