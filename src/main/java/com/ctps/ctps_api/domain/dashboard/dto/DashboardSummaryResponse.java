package com.ctps.ctps_api.domain.dashboard.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardSummaryResponse {

    private int totalProblems;
    private int solvedProblems;
    private int reviewNeededProblems;
    private int bookmarkedProblems;
    private int todaySolvedProblems;
    private long reviewsCompletedThisWeek;
    private long reviewsCompletedThisMonth;
    private double averageReviewCount;
    private List<DashboardTagStatResponse> topTags;
}
