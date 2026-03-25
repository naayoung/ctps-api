package com.ctps.ctps_api.domain.problem.controller;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemSearchService;
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
@RequestMapping("/api/external-problems")
@RequiredArgsConstructor
public class ExternalProblemController {

    private final ExternalProblemSearchService externalProblemSearchService;
    private final InMemoryRateLimitService rateLimitService;
    private final ClientRequestResolver clientRequestResolver;

    @Value("${security.rate-limit.external-search.max-requests:60}")
    private int externalSearchMaxRequests;

    @Value("${security.rate-limit.external-search.window-seconds:60}")
    private long externalSearchWindowSeconds;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ExternalProblemSearchResponse>> searchExternalProblems(
            @Valid ProblemSearchRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String principalKey = CurrentUserContext.getOptional()
                .map(user -> "user:" + user.getId())
                .orElse("guest");
        String clientKey = principalKey + ":" + clientRequestResolver.resolveClientKey(httpServletRequest);
        rateLimitService.check(
                "external-search:" + clientKey,
                externalSearchMaxRequests,
                Duration.ofSeconds(externalSearchWindowSeconds),
                "외부 검색 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
        );
        return ResponseEntity.ok(
                ApiResponse.success("외부 문제 검색 성공", externalProblemSearchService.search(request))
        );
    }
}
