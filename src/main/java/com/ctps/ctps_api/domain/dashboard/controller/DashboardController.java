package com.ctps.ctps_api.domain.dashboard.controller;

import com.ctps.ctps_api.domain.dashboard.dto.DashboardSummaryResponse;
import com.ctps.ctps_api.domain.dashboard.service.DashboardService;
import com.ctps.ctps_api.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success("대시보드 요약 조회 성공", dashboardService.getSummary()));
    }
}
