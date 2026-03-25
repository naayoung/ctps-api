package com.ctps.ctps_api.domain.review.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReviewHistoryResponse {

    private Long problemId;
    private String problemTitle;
    private int totalReviewCount;
    private List<ReviewHistoryItemResponse> entries;
}
