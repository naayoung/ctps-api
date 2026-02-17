package com.ctps.ctps_api.domain.review.service;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ProblemRepository;
import com.ctps.ctps_api.domain.review.dto.ReviewCheckResponse;
import com.ctps.ctps_api.domain.review.dto.TodayReviewResponse;
import com.ctps.ctps_api.domain.review.entity.Review;
import com.ctps.ctps_api.domain.review.repository.ReviewRepository;
import com.ctps.ctps_api.global.exception.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProblemRepository problemRepository;

    @Transactional
    public ReviewCheckResponse checkReview(Long problemId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new NotFoundException("문제를 찾을 수 없습니다. id=" + problemId));

        LocalDate today = LocalDate.now();
        Review review = reviewRepository.findByProblemId(problemId)
                .orElseGet(() -> reviewRepository.save(Review.builder()
                        .problem(problem)
                        .reviewCount(0)
                        .lastReviewedDate(today)
                        .nextReviewDate(today)
                        .build()));

        review.check(today);

        return ReviewCheckResponse.builder()
                .problemId(problemId)
                .reviewCount(review.getReviewCount())
                .lastReviewedDate(review.getLastReviewedDate())
                .nextReviewDate(review.getNextReviewDate())
                .build();
    }

    public List<TodayReviewResponse> getTodayReviews(LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;

        return reviewRepository.findAllByNextReviewDateLessThanEqual(targetDate).stream()
                .map(review -> TodayReviewResponse.builder()
                        .reviewId(review.getId())
                        .problemId(review.getProblem().getId())
                        .problemTitle(review.getProblem().getPlatform() + " #" + review.getProblem().getNumber())
                        .platform(review.getProblem().getPlatform())
                        .level(review.getProblem().getDifficulty().name())
                        .reviewCount(review.getReviewCount())
                        .nextReviewDate(review.getNextReviewDate())
                        .build())
                .toList();
    }
}
