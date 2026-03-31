package com.ctps.ctps_api.domain.review.service;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ProblemRepository;
import com.ctps.ctps_api.domain.problem.service.ProblemActivityService;
import com.ctps.ctps_api.domain.review.dto.ReviewCheckRequest;
import com.ctps.ctps_api.domain.review.dto.ReviewCheckResponse;
import com.ctps.ctps_api.domain.review.dto.ReviewHistoryItemResponse;
import com.ctps.ctps_api.domain.review.dto.ReviewHistoryResponse;
import com.ctps.ctps_api.domain.review.dto.TodayReviewResponse;
import com.ctps.ctps_api.domain.review.entity.ReviewHistoryEntry;
import com.ctps.ctps_api.domain.review.entity.Review;
import com.ctps.ctps_api.domain.review.policy.ReviewSchedulePolicy;
import com.ctps.ctps_api.domain.review.repository.ReviewHistoryRepository;
import com.ctps.ctps_api.domain.review.repository.ReviewRepository;
import com.ctps.ctps_api.global.exception.NotFoundException;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import com.ctps.ctps_api.global.time.DateTimeSupport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewHistoryRepository reviewHistoryRepository;
    private final ProblemRepository problemRepository;
    private final ProblemActivityService problemActivityService;
    private final ReviewSchedulePolicy reviewSchedulePolicy;
    private final Clock clock;

    @Transactional
    public ReviewCheckResponse checkReview(Long problemId, ReviewCheckRequest request) {
        Long userId = CurrentUserContext.getRequired().getId();
        Problem problem = problemRepository.findByIdAndUserId(problemId, userId)
                .orElseThrow(() -> new NotFoundException("문제를 찾을 수 없습니다. id=" + problemId));

        LocalDate today = DateTimeSupport.todayInSeoul(clock);
        LocalDateTime completedAt = DateTimeSupport.nowUtc(clock);
        Review review = reviewRepository.findByProblemIdAndProblemUserId(problemId, userId)
                .orElseGet(() -> reviewRepository.save(Review.builder()
                        .problem(problem)
                        .reviewCount(countReviewsSinceLatestSolve(problem))
                        .lastReviewedDate(resolveCycleLastReviewedDate(problem, today))
                        .nextReviewDate(resolveNextReviewDate(problem, today))
                        .build()));

        boolean alreadyReviewedToday = today.equals(review.getLastReviewedDate()) && review.getReviewCount() > 0;
        int reviewCountAfterCheck;
        int intervalDays;
        LocalDate nextReviewDate;

        if (alreadyReviewedToday) {
            reviewCountAfterCheck = review.getReviewCount();
            nextReviewDate = review.getNextReviewDate();
            intervalDays = (int) Math.max(0, ChronoUnit.DAYS.between(today, nextReviewDate));
        } else {
            int nextReviewCount = review.getReviewCount() + 1;
            intervalDays = reviewSchedulePolicy.resolveIntervalDays(nextReviewCount);
            nextReviewDate = reviewSchedulePolicy.calculateNextReviewDate(nextReviewCount, today);
            review.completeReview(today, nextReviewDate);
            reviewCountAfterCheck = review.getReviewCount();
        }

        if (request != null) {
            problem.updateLatestOutcome(request.getResult(), request.getMemo());
        }
        problem.markReviewCompleted(today);
        reviewHistoryRepository.save(ReviewHistoryEntry.builder()
                .review(review)
                .problem(problem)
                .user(problem.getUser())
                .reviewCountAfterCheck(reviewCountAfterCheck)
                .intervalDays(intervalDays)
                .reviewedAt(today)
                .nextReviewDate(nextReviewDate)
                .createdAt(completedAt)
                .build());
        problemActivityService.recordReviewCompletion(
                problem,
                request == null ? problem.getResult() : request.getResult(),
                request == null ? problem.getMemo() : request.getMemo(),
                completedAt
        );

        return ReviewCheckResponse.builder()
                .problemId(problemId)
                .reviewCount(reviewCountAfterCheck)
                .lastReviewedDate(review.getLastReviewedDate())
                .nextReviewDate(nextReviewDate)
                .intervalDays(intervalDays)
                .build();
    }

    public List<TodayReviewResponse> getTodayReviews(LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        Long userId = CurrentUserContext.getRequired().getId();

        return reviewRepository.findAllByProblemUserIdAndNextReviewDateLessThanEqual(userId, targetDate).stream()
                .filter(review -> review.getProblem().isNeedsReview())
                .map(review -> TodayReviewResponse.builder()
                        .reviewId(review.getId())
                        .problemId(review.getProblem().getId())
                        .problemTitle(review.getProblem().getPlatform() + " #" + review.getProblem().getNumber())
                        .platform(review.getProblem().getPlatform())
                        .level(review.getProblem().getDifficulty() != null
                                ? review.getProblem().getDifficulty().name()
                                : "medium")
                        .reviewCount(review.getReviewCount())
                        .nextReviewDate(review.getNextReviewDate())
                        .overdueDays((int) Math.max(0, ChronoUnit.DAYS.between(review.getNextReviewDate(), targetDate)))
                        .build())
                .toList();
    }

    public ReviewHistoryResponse getReviewHistory(Long problemId) {
        Long userId = CurrentUserContext.getRequired().getId();
        Problem problem = problemRepository.findByIdAndUserId(problemId, userId)
                .orElseThrow(() -> new NotFoundException("문제를 찾을 수 없습니다. id=" + problemId));

        List<ReviewHistoryItemResponse> entries = reviewHistoryRepository
                .findAllByProblemIdAndUserIdOrderByReviewedAtDesc(problemId, userId)
                .stream()
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(),
                        ReviewDateAggregationHelper::aggregateByDate
                ))
                .stream()
                .map(ReviewHistoryItemResponse::from)
                .toList();

        return ReviewHistoryResponse.builder()
                .problemId(problemId)
                .problemTitle(problem.getTitle())
                .totalReviewCount((int) entries.stream()
                        .map(ReviewHistoryItemResponse::getReviewedAt)
                        .distinct()
                        .count())
                .entries(entries)
                .build();
    }

    private int countReviewsSinceLatestSolve(Problem problem) {
        LocalDate latestSolvedDate = problem.getLastSolvedAt();
        return (int) ReviewDateAggregationHelper.countDistinctDatesSince(problem.getReviewHistory(), latestSolvedDate);
    }

    private LocalDate resolveCycleLastReviewedDate(Problem problem, LocalDate fallbackDate) {
        LocalDate latestSolvedDate = problem.getLastSolvedAt();

        if (problem.getReviewHistory() != null && !problem.getReviewHistory().isEmpty()) {
            return problem.getReviewHistory().stream()
                    .filter(reviewedDate -> latestSolvedDate == null || !reviewedDate.isBefore(latestSolvedDate))
                    .max(Comparator.naturalOrder())
                    .orElse(latestSolvedDate != null ? latestSolvedDate : fallbackDate);
        }

        if (latestSolvedDate != null) {
            return latestSolvedDate;
        }

        if (problem.getReviewedAt() != null) {
            return problem.getReviewedAt();
        }

        return fallbackDate;
    }

    private LocalDate resolveNextReviewDate(Problem problem, LocalDate fallbackDate) {
        int reviewCount = countReviewsSinceLatestSolve(problem);
        LocalDate lastReviewedDate = resolveCycleLastReviewedDate(problem, fallbackDate);
        if (reviewCount <= 0) {
            return fallbackDate;
        }
        return reviewSchedulePolicy.calculateNextReviewDate(reviewCount, lastReviewedDate);
    }
}
