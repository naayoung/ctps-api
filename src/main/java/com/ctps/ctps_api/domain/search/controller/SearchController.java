package com.ctps.ctps_api.domain.search.controller;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.search.dto.UnifiedSearchResponse;
import com.ctps.ctps_api.domain.search.service.UnifiedSearchService;
import com.ctps.ctps_api.global.response.ApiResponse;
import com.ctps.ctps_api.global.security.ClientRequestResolver;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import com.ctps.ctps_api.global.security.InMemoryRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final UnifiedSearchService unifiedSearchService;
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
}
