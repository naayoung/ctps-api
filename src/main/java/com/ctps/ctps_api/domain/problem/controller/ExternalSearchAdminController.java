package com.ctps.ctps_api.domain.problem.controller;

import com.ctps.ctps_api.domain.problem.dto.admin.ExternalSearchMetricsResponse;
import com.ctps.ctps_api.domain.problem.dto.admin.ProgrammersCatalogIngestResponse;
import com.ctps.ctps_api.domain.problem.service.ExternalSearchMetricsService;
import com.ctps.ctps_api.domain.problem.service.ProgrammersCatalogIngestionService;
import com.ctps.ctps_api.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/external-search")
@RequiredArgsConstructor
public class ExternalSearchAdminController {

    private final ExternalSearchMetricsService externalSearchMetricsService;
    private final ProgrammersCatalogIngestionService programmersCatalogIngestionService;

    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<ExternalSearchMetricsResponse>> getMetrics() {
        return ResponseEntity.ok(
                ApiResponse.success("외부 검색 메트릭 조회 성공", externalSearchMetricsService.snapshot())
        );
    }

    @PostMapping("/programmers/ingest")
    public ResponseEntity<ApiResponse<ProgrammersCatalogIngestResponse>> ingestProgrammersCatalog() {
        int count = programmersCatalogIngestionService.ingestFromConfiguredFeed();
        return ResponseEntity.ok(
                ApiResponse.success(
                        "프로그래머스 카탈로그 적재 성공",
                        ProgrammersCatalogIngestResponse.builder()
                                .importedCount(count)
                                .success(true)
                                .build()
                )
        );
    }
}
