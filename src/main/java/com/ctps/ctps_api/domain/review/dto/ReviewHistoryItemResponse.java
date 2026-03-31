package com.ctps.ctps_api.domain.review.dto;

import com.ctps.ctps_api.domain.review.entity.ReviewHistoryEntry;
import com.ctps.ctps_api.domain.review.service.ReviewDateAggregationHelper;
import com.ctps.ctps_api.global.time.DateTimeSupport;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
    private int executionCount;
    private List<OffsetDateTime> executedAtTimes;

    public static ReviewHistoryItemResponse from(ReviewHistoryEntry entry) {
        return ReviewHistoryItemResponse.builder()
                .id(entry.getId())
                .reviewedAt(entry.getReviewedAt())
                .nextReviewDate(entry.getNextReviewDate())
                .intervalDays(entry.getIntervalDays())
                .reviewCountAfterCheck(entry.getReviewCountAfterCheck())
                .executionCount(1)
                .executedAtTimes(List.of(DateTimeSupport.asUtcOffsetDateTime(entry.getCreatedAt())))
                .build();
    }

    public static ReviewHistoryItemResponse from(ReviewDateAggregationHelper.DailyReviewLog dailyLog) {
        return ReviewHistoryItemResponse.builder()
                .reviewedAt(dailyLog.reviewedAt())
                .nextReviewDate(dailyLog.nextReviewDate())
                .intervalDays(dailyLog.intervalDays())
                .reviewCountAfterCheck(dailyLog.reviewCountAfterCheck())
                .executionCount(dailyLog.executionCount())
                .executedAtTimes(dailyLog.executedAtTimes().stream()
                        .map(DateTimeSupport::asUtcOffsetDateTime)
                        .toList())
                .build();
    }
}
