package com.ctps.ctps_api.domain.search.service;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.ProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.search.ProcessedSearchQuery;
import com.ctps.ctps_api.domain.problem.service.search.SearchQueryPreprocessor;
import com.ctps.ctps_api.domain.search.dto.SearchRankingType;
import com.ctps.ctps_api.domain.search.dto.UnifiedSearchDebugResponse;
import com.ctps.ctps_api.domain.search.dto.UnifiedSearchItemResponse;
import com.ctps.ctps_api.domain.search.dto.UnifiedSearchResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UnifiedSearchService {

    private final ProblemSearchService problemSearchService;
    private final ExternalProblemSearchService externalProblemSearchService;
    private final SearchQueryPreprocessor searchQueryPreprocessor;
    private final SearchCandidateCollector searchCandidateCollector;
    private final UnifiedSearchRankingService rankingService;
    private final UserPreferenceAnalyzer userPreferenceAnalyzer;
    private final SearchResultAssembler searchResultAssembler;

    public UnifiedSearchResponse search(ProblemSearchRequest request) {
        int targetSize = Math.min(request.getSize(), 15);
        int aggregateSize = Math.max(targetSize * 3, 30);
        ProblemSearchRequest aggregateRequest = request.copyWithPageAndSize(0, aggregateSize);
        ProcessedSearchQuery processedQuery = searchQueryPreprocessor.process(aggregateRequest);
        UserPreferenceProfile preferenceProfile = userPreferenceAnalyzer.analyze();

        SearchCandidateCollector.CandidateCollection candidates = searchCandidateCollector.collect(
                aggregateRequest,
                processedQuery,
                preferenceProfile,
                aggregateSize,
                targetSize
        );

        List<UnifiedSearchRankingResult> ranked = rankingService.rank(
                candidates,
                processedQuery,
                request.getSortOption(),
                preferenceProfile,
                request.isDebugEnabled()
        );
        List<UnifiedSearchRankingResult> assembled = searchResultAssembler.assemble(ranked, targetSize);
        List<UnifiedSearchItemResponse> pageItems = assembled.stream().map(UnifiedSearchRankingResult::getItem).toList();
        UnifiedSearchDebugResponse debugResponse = request.isDebugEnabled()
                ? buildDebugResponse(candidates, ranked, pageItems)
                : null;

        int totalElements = pageItems.size();
        int totalPages = totalElements == 0 ? 0 : 1;
        ExternalProblemSearchResponse externalResponse = externalProblemSearchService.search(aggregateRequest);

        if (request.isDebugEnabled()) {
            log.info(
                    "unified search debug keyword='{}' exactCandidates={} partialCandidates={} fallbackCandidates={} deduplicated={} rankingTypes={}",
                    request.getKeyword(),
                    debugResponse.getExactCandidatesCount(),
                    debugResponse.getPartialCandidatesCount(),
                    debugResponse.getFallbackCandidatesCount(),
                    debugResponse.getDeduplicatedCount(),
                    debugResponse.getRankingTypeCounts()
            );
        }

        return UnifiedSearchResponse.builder()
                .query(processedQuery.getRawKeyword())
                .normalizedTokens(processedQuery.getKeywordTokens())
                .page(0)
                .size(targetSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .hasNext(false)
                .internalCount((int) candidates.getExactCandidates().stream()
                        .filter(item -> item.getSource() == com.ctps.ctps_api.domain.search.dto.SearchItemSource.INTERNAL)
                        .count())
                .externalCount((int) Math.min(Integer.MAX_VALUE, externalResponse.getTotalElements()))
                .failedExternalProviders(externalResponse.getFailedProviders())
                .externalWarning(externalResponse.getWarningMessage())
                .items(pageItems)
                .debug(debugResponse)
                .build();
    }

    private UnifiedSearchDebugResponse buildDebugResponse(
            SearchCandidateCollector.CandidateCollection candidates,
            List<UnifiedSearchRankingResult> ranked,
            List<UnifiedSearchItemResponse> pageItems
    ) {
        Map<SearchRankingType, Long> rankingTypeCounts = pageItems.stream()
                .map(UnifiedSearchItemResponse::getRankingType)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(
                        type -> type,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));
        return UnifiedSearchDebugResponse.builder()
                .exactCandidatesCount(candidates.getExactCandidates().size())
                .partialCandidatesCount(candidates.getPartialCandidates().size())
                .fallbackCandidatesCount(candidates.getFallbackCandidates().size())
                .deduplicatedCount(ranked.size())
                .rankingTypeCounts(rankingTypeCounts)
                .build();
    }
}
