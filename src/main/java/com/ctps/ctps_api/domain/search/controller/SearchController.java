package com.ctps.ctps_api.domain.search.controller;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.search.dto.UnifiedSearchResponse;
import com.ctps.ctps_api.domain.search.service.UnifiedSearchService;
import com.ctps.ctps_api.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final UnifiedSearchService unifiedSearchService;

    @GetMapping
    public ResponseEntity<ApiResponse<UnifiedSearchResponse>> search(@Valid ProblemSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("통합 검색 성공", unifiedSearchService.search(request)));
    }
}
