package com.ctps.ctps_api.domain.review.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigurableReviewSchedulePolicyTest {

    @Test
    @DisplayName("복습 주기는 설정된 간격에 따라 다음 복습일을 계산한다")
    void calculateNextReviewDate_usesConfiguredIntervals() {
        ConfigurableReviewSchedulePolicy policy = new ConfigurableReviewSchedulePolicy("1,3,7,14,30");
        LocalDate reviewedDate = LocalDate.of(2026, 3, 26);

        assertThat(policy.resolveIntervalDays(1)).isEqualTo(1);
        assertThat(policy.resolveIntervalDays(2)).isEqualTo(3);
        assertThat(policy.resolveIntervalDays(5)).isEqualTo(30);
        assertThat(policy.resolveIntervalDays(8)).isEqualTo(30);
        assertThat(policy.calculateNextReviewDate(3, reviewedDate)).isEqualTo(LocalDate.of(2026, 4, 2));
    }
}
