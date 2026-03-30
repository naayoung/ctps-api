package com.ctps.ctps_api.domain.dashboard.service;

import com.ctps.ctps_api.domain.dashboard.dto.DashboardSummaryResponse;
import com.ctps.ctps_api.domain.dashboard.dto.DashboardTagStatResponse;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ProblemRepository;
import com.ctps.ctps_api.domain.review.repository.ReviewHistoryRepository;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final ProblemRepository problemRepository;
    private final ReviewHistoryRepository reviewHistoryRepository;

    public DashboardSummaryResponse getSummary() {
        Long userId = CurrentUserContext.getRequired().getId();
        List<Problem> problems = problemRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);

        long reviewsCompletedThisWeek = reviewHistoryRepository.countByUserIdAndReviewedAtBetween(userId, weekStart, today);
        long reviewsCompletedThisMonth = reviewHistoryRepository.countByUserIdAndReviewedAtBetween(userId, monthStart, today);
        double averageReviewCount = problems.isEmpty()
                ? 0
                : problems.stream().mapToInt(problem -> problem.getReviewHistory().size()).average().orElse(0);

        Map<String, Long> tagCounts = problems.stream()
                .flatMap(problem -> problem.getTags().stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        List<DashboardTagStatResponse> topTags = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(5)
                .map(entry -> DashboardTagStatResponse.builder()
                        .tag(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();

        return DashboardSummaryResponse.builder()
                .totalProblems(problems.size())
                .solvedProblems((int) problems.stream().filter(problem -> problem.getResult() == Problem.Result.success).count())
                .reviewNeededProblems((int) problems.stream()
                        .filter(problem -> problem.isNeedsReview() && !problem.isBookmarked())
                        .count())
                .bookmarkedProblems((int) problems.stream().filter(Problem::isBookmarked).count())
                .todaySolvedProblems((int) problems.stream()
                        .filter(problem -> problem.getSolvedDates() != null && problem.getSolvedDates().contains(today))
                        .count())
                .reviewsCompletedThisWeek(reviewsCompletedThisWeek)
                .reviewsCompletedThisMonth(reviewsCompletedThisMonth)
                .averageReviewCount(averageReviewCount)
                .topTags(topTags)
                .build();
    }
}
