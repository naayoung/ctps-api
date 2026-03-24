package com.ctps.ctps_api.domain.problem.controller;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemSearchService;
import com.ctps.ctps_api.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/external-problems")
@RequiredArgsConstructor
public class ExternalProblemController {

    private final ExternalProblemSearchService externalProblemSearchService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ExternalProblemSearchResponse>> searchExternalProblems(
            @Valid ProblemSearchRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("외부 문제 검색 성공", externalProblemSearchService.search(request))
        );
    }
}
