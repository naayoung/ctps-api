package com.ctps.ctps_api.domain.review.policy;

import java.time.LocalDate;

public interface ReviewSchedulePolicy {

    LocalDate calculateNextReviewDate(int nextReviewCount, LocalDate reviewedDate);

    int resolveIntervalDays(int nextReviewCount);
}
