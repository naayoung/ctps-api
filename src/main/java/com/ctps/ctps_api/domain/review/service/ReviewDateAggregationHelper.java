package com.ctps.ctps_api.domain.review.service;

import com.ctps.ctps_api.domain.review.entity.ReviewHistoryEntry;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReviewDateAggregationHelper {

    private ReviewDateAggregationHelper() {
    }

    public static long countDistinctDates(List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return 0;
        }

        return dates.stream()
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    public static long countDistinctDatesSince(List<LocalDate> dates, LocalDate startDateInclusive) {
        if (dates == null || dates.isEmpty()) {
            return 0;
        }

        return dates.stream()
                .filter(Objects::nonNull)
                .filter(date -> startDateInclusive == null || !date.isBefore(startDateInclusive))
                .distinct()
                .count();
    }

    public static List<DailyReviewLog> aggregateByDate(List<ReviewHistoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        Map<LocalDate, List<ReviewHistoryEntry>> groupedByDate = entries.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(
                        ReviewHistoryEntry::getReviewedAt,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        return groupedByDate.entrySet().stream()
                .map(entry -> toDailyReviewLog(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(DailyReviewLog::reviewedAt).reversed())
                .toList();
    }

    public static long countDistinctProblemDates(List<ReviewHistoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        return entries.stream()
                .filter(Objects::nonNull)
                .map(entry -> entry.getProblem().getId() + ":" + entry.getReviewedAt())
                .distinct()
                .count();
    }

    private static DailyReviewLog toDailyReviewLog(LocalDate reviewedAt, List<ReviewHistoryEntry> entries) {
        ReviewHistoryEntry latestEntry = entries.stream()
                .max(Comparator.comparing(ReviewHistoryEntry::getCreatedAt))
                .orElseThrow();

        List<LocalDateTime> executedAtTimes = entries.stream()
                .map(ReviewHistoryEntry::getCreatedAt)
                .sorted(Comparator.reverseOrder())
                .toList();

        return new DailyReviewLog(
                reviewedAt,
                latestEntry.getNextReviewDate(),
                latestEntry.getIntervalDays(),
                latestEntry.getReviewCountAfterCheck(),
                executedAtTimes,
                executedAtTimes.size()
        );
    }

    public record DailyReviewLog(
            LocalDate reviewedAt,
            LocalDate nextReviewDate,
            int intervalDays,
            int reviewCountAfterCheck,
            List<LocalDateTime> executedAtTimes,
            int executionCount
    ) {
    }
}
