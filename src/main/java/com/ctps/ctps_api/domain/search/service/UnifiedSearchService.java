package com.ctps.ctps_api.domain.search.service;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.ProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.search.ProcessedSearchQuery;
import com.ctps.ctps_api.domain.problem.service.search.SearchQueryPreprocessor;
import com.ctps.ctps_api.domain.search.dto.UnifiedSearchItemResponse;
import com.ctps.ctps_api.domain.search.dto.UnifiedSearchResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnifiedSearchService {

    private final ProblemSearchService problemSearchService;
    private final ExternalProblemSearchService externalProblemSearchService;
    private final SearchQueryPreprocessor searchQueryPreprocessor;
    private final UnifiedSearchResponseMapper responseMapper;
    private final UnifiedSearchRankingService rankingService;

    public UnifiedSearchResponse search(ProblemSearchRequest request) {
        int aggregateSize = Math.max((request.getPage() + 1) * request.getSize() * 3, 30);
        ProblemSearchRequest aggregateRequest = request.copyWithPageAndSize(0, aggregateSize);
        ProcessedSearchQuery processedQuery = searchQueryPreprocessor.process(aggregateRequest);

        ProblemSearchResponse internalResponse = problemSearchService.search(aggregateRequest);
        ExternalProblemSearchResponse externalResponse = externalProblemSearchService.search(aggregateRequest);

        List<UnifiedSearchItemResponse> candidates = new ArrayList<>();
        internalResponse.getContent().stream().map(responseMapper::fromInternal).forEach(candidates::add);
        externalResponse.getContent().stream().map(responseMapper::fromExternal).forEach(candidates::add);

        List<UnifiedSearchRankingResult> ranked = rankingService.rank(candidates, processedQuery, request.getSortOption());
        int start = request.getPage() * request.getSize();
        int end = Math.min(start + request.getSize(), ranked.size());
        List<UnifiedSearchItemResponse> pageItems = start >= ranked.size()
                ? List.of()
                : ranked.subList(start, end).stream().map(UnifiedSearchRankingResult::getItem).toList();

        long totalElements = internalResponse.getTotalElements() + externalResponse.getTotalElements();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / request.getSize());

        return UnifiedSearchResponse.builder()
                .query(processedQuery.getRawKeyword())
                .normalizedTokens(processedQuery.getKeywordTokens())
                .page(request.getPage())
                .size(request.getSize())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .hasNext((long) (request.getPage() + 1) * request.getSize() < totalElements)
                .internalCount((int) Math.min(Integer.MAX_VALUE, internalResponse.getTotalElements()))
                .externalCount((int) Math.min(Integer.MAX_VALUE, externalResponse.getTotalElements()))
                .items(pageItems)
                .build();
    }
}
