package com.ctps.ctps_api.domain.review.policy;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableReviewSchedulePolicy implements ReviewSchedulePolicy {

    private final List<Integer> intervals;

    public ConfigurableReviewSchedulePolicy(
            @Value("${review.schedule.intervals-days:1,3,7,14,30}") String intervalsExpression
    ) {
        this.intervals = Arrays.stream(intervalsExpression.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Integer::parseInt)
                .toList();
    }

    @Override
    public LocalDate calculateNextReviewDate(int nextReviewCount, LocalDate reviewedDate) {
        return reviewedDate.plusDays(resolveIntervalDays(nextReviewCount));
    }

    @Override
    public int resolveIntervalDays(int nextReviewCount) {
        int index = Math.max(nextReviewCount - 1, 0);
        if (intervals.isEmpty()) {
            return 1;
        }
        if (index >= intervals.size()) {
            return intervals.get(intervals.size() - 1);
        }
        return intervals.get(index);
    }
}
