package com.ctps.ctps_api.domain.review.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReviewCheckResponse {

    private Long problemId;
    private int reviewCount;
    private LocalDate lastReviewedDate;
    private LocalDate nextReviewDate;
    private int intervalDays;
}
