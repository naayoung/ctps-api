package com.ctps.ctps_api.domain.review.service;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ProblemRepository;
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
    private final ReviewSchedulePolicy reviewSchedulePolicy;

    @Transactional
    public ReviewCheckResponse checkReview(Long problemId) {
        Long userId = CurrentUserContext.getRequired().getId();
        Problem problem = problemRepository.findByIdAndUserId(problemId, userId)
                .orElseThrow(() -> new NotFoundException("문제를 찾을 수 없습니다. id=" + problemId));

        LocalDate today = LocalDate.now();
        Review review = reviewRepository.findByProblemIdAndProblemUserId(problemId, userId)
                .orElseGet(() -> reviewRepository.save(Review.builder()
                        .problem(problem)
                        .reviewCount(countReviewsSinceLatestSolve(problem))
                        .lastReviewedDate(resolveCycleLastReviewedDate(problem, today))
                        .nextReviewDate(today)
                        .build()));

        int nextReviewCount = review.getReviewCount() + 1;
        int intervalDays = reviewSchedulePolicy.resolveIntervalDays(nextReviewCount);
        LocalDate nextReviewDate = reviewSchedulePolicy.calculateNextReviewDate(nextReviewCount, today);

        review.completeReview(today, nextReviewDate);
        problem.markReviewCompleted(today);
        reviewHistoryRepository.save(ReviewHistoryEntry.builder()
                .review(review)
                .problem(problem)
                .user(problem.getUser())
                .reviewCountAfterCheck(review.getReviewCount())
                .intervalDays(intervalDays)
                .reviewedAt(today)
                .nextReviewDate(nextReviewDate)
                .createdAt(LocalDateTime.now())
                .build());

        return ReviewCheckResponse.builder()
                .problemId(problemId)
                .reviewCount(review.getReviewCount())
                .lastReviewedDate(review.getLastReviewedDate())
                .nextReviewDate(review.getNextReviewDate())
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
                .map(ReviewHistoryItemResponse::from)
                .toList();

        return ReviewHistoryResponse.builder()
                .problemId(problemId)
                .problemTitle(problem.getTitle())
                .totalReviewCount(entries.size())
                .entries(entries)
                .build();
    }

    private int countReviewsSinceLatestSolve(Problem problem) {
        if (problem.getReviewHistory() == null || problem.getReviewHistory().isEmpty()) {
            return 0;
        }

        LocalDate latestSolvedDate = problem.getLastSolvedAt();
        if (latestSolvedDate == null) {
            return problem.getReviewHistory().size();
        }

        return (int) problem.getReviewHistory().stream()
                .filter(reviewedDate -> !reviewedDate.isBefore(latestSolvedDate))
                .count();
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
}
