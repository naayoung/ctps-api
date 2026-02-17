package com.ctps.ctps_api.domain.review.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TodayReviewResponse {

    private Long reviewId;
    private Long problemId;
    private String problemTitle;
    private String platform;
    private String level;
    private int reviewCount;
    private LocalDate nextReviewDate;
}
