package com.ctps.ctps_api.domain.review.dto;

import com.ctps.ctps_api.domain.review.entity.ReviewHistoryEntry;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReviewHistoryItemResponse {

    private Long id;
    private LocalDate reviewedAt;
    private LocalDate nextReviewDate;
    private int intervalDays;
    private int reviewCountAfterCheck;

    public static ReviewHistoryItemResponse from(ReviewHistoryEntry entry) {
        return ReviewHistoryItemResponse.builder()
                .id(entry.getId())
                .reviewedAt(entry.getReviewedAt())
                .nextReviewDate(entry.getNextReviewDate())
                .intervalDays(entry.getIntervalDays())
                .reviewCountAfterCheck(entry.getReviewCountAfterCheck())
                .build();
    }
}
