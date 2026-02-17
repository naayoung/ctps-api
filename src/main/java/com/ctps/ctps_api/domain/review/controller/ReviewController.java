package com.ctps.ctps_api.domain.review.controller;

import com.ctps.ctps_api.domain.review.dto.ReviewCheckResponse;
import com.ctps.ctps_api.domain.review.dto.TodayReviewResponse;
import com.ctps.ctps_api.domain.review.service.ReviewService;
import com.ctps.ctps_api.global.response.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/{problemId}/check")
    public ResponseEntity<ApiResponse<ReviewCheckResponse>> checkReview(@PathVariable Long problemId) {
        ReviewCheckResponse response = reviewService.checkReview(problemId);
        return ResponseEntity.ok(ApiResponse.success("복습 체크 성공", response));
    }

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<TodayReviewResponse>>> getTodayReviews(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        List<TodayReviewResponse> response = reviewService.getTodayReviews(date);
        return ResponseEntity.ok(ApiResponse.success("오늘 복습 목록 조회 성공", response));
    }
}
