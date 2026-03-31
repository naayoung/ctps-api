package com.ctps.ctps_api.domain.search.controller;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.search.dto.FrequentSearchTypesResponse;
import com.ctps.ctps_api.domain.search.dto.ProblemInteractionEventRequest;
import com.ctps.ctps_api.domain.search.dto.SearchEventRequest;
import com.ctps.ctps_api.domain.search.dto.UnifiedSearchResponse;
import com.ctps.ctps_api.domain.search.service.FrequentSearchTypeService;
import com.ctps.ctps_api.domain.search.service.SearchActivityService;
import com.ctps.ctps_api.domain.search.service.UnifiedSearchService;
import com.ctps.ctps_api.global.response.ApiResponse;
import com.ctps.ctps_api.global.security.ClientRequestResolver;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import com.ctps.ctps_api.global.security.InMemoryRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final UnifiedSearchService unifiedSearchService;
    private final SearchActivityService searchActivityService;
    private final FrequentSearchTypeService frequentSearchTypeService;
    private final InMemoryRateLimitService rateLimitService;
    private final ClientRequestResolver clientRequestResolver;

    @Value("${security.rate-limit.search.max-requests:120}")
    private int searchMaxRequests;

    @Value("${security.rate-limit.search.window-seconds:60}")
    private long searchWindowSeconds;

    @GetMapping
    public ResponseEntity<ApiResponse<UnifiedSearchResponse>> search(
            @Valid ProblemSearchRequest request,
            HttpServletRequest httpServletRequest
    ) {
        log.info(
                "search request keywordLength={} platformCount={} difficultyCount={} tagCount={} sort={} page={} size={}",
                request.getKeyword() == null ? 0 : request.getKeyword().length(),
                request.getPlatform().size(),
                request.getDifficulty().size(),
                request.getTags().size(),
                request.getSort(),
                request.getPage(),
                request.getSize()
        );
        String principalKey = CurrentUserContext.getOptional()
                .map(user -> "user:" + user.getId())
                .orElse("guest");
        String clientKey = principalKey + ":" + clientRequestResolver.resolveClientKey(httpServletRequest);
        rateLimitService.check(
                "search:" + clientKey,
                searchMaxRequests,
                Duration.ofSeconds(searchWindowSeconds),
                "검색 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
        );
        return ResponseEntity.ok(ApiResponse.success("통합 검색 성공", unifiedSearchService.search(request)));
    }

    @GetMapping("/frequent-types")
    public ResponseEntity<ApiResponse<FrequentSearchTypesResponse>> getFrequentTypes() {
        return ResponseEntity.ok(ApiResponse.success("자주 찾는 유형 조회 성공", frequentSearchTypeService.getFrequentTypes()));
    }

    @PostMapping("/events")
    public ResponseEntity<ApiResponse<Void>> recordSearchEvent(@Valid @RequestBody SearchEventRequest request) {
        searchActivityService.recordSearchEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("검색 이벤트 기록 성공"));
    }

    @PostMapping("/events/problem-interactions")
    public ResponseEntity<ApiResponse<Void>> recordProblemInteractionEvent(
            @Valid @RequestBody ProblemInteractionEventRequest request
    ) {
        searchActivityService.recordInteractionEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("문제 상호작용 이벤트 기록 성공"));
    }
}
