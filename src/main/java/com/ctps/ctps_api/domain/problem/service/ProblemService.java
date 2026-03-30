package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.auth.repository.UserRepository;
import com.ctps.ctps_api.domain.problem.dto.ProblemCreateRequest;
import com.ctps.ctps_api.domain.problem.dto.ProblemResponse;
import com.ctps.ctps_api.domain.problem.dto.ProblemUpdateRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ProblemRepository;
import com.ctps.ctps_api.domain.review.entity.Review;
import com.ctps.ctps_api.domain.review.repository.ReviewRepository;
import com.ctps.ctps_api.domain.search.service.SearchActivityService;
import com.ctps.ctps_api.global.exception.NotFoundException;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final SearchActivityService searchActivityService;

    @Transactional
    public ProblemResponse createProblem(ProblemCreateRequest request) {
        User user = userRepository.getReferenceById(CurrentUserContext.getRequired().getId());
        Problem problem = Problem.builder()
                .user(user)
                .platform(request.getPlatform())
                .title(resolveTitle(request.getTitle(), request.getPlatform(), request.getNumber()))
                .number(request.getNumber())
                .link(request.getLink())
                .tags(request.getTags())
                .difficulty(request.getDifficulty())
                .memo(request.getMemo())
                .result(request.getResult())
                .needsReview(request.isNeedsReview())
                .reviewedAt(request.getReviewedAt())
                .reviewHistory(request.getReviewHistory())
                .createdAt(LocalDateTime.now())
                .solvedDates(request.getSolvedDates())
                .lastSolvedAt(request.getLastSolvedAt())
                .bookmarked(request.isBookmarked())
                .build();

        Problem saved = problemRepository.save(problem);
        syncReviewState(saved);
        if (saved.isBookmarked()) {
            searchActivityService.recordBookmarkEvent(saved);
        }
        if (saved.isNeedsReview()) {
            searchActivityService.recordMarkReviewEvent(saved);
        }
        return ProblemResponse.from(saved);
    }

    public List<ProblemResponse> getProblems() {
        Long userId = CurrentUserContext.getRequired().getId();
        return problemRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(ProblemResponse::from).toList();
    }

    public ProblemResponse getProblem(Long id) {
        return ProblemResponse.from(findById(id));
    }

    @Transactional
    public ProblemResponse updateProblem(Long id, ProblemUpdateRequest request) {
        Problem problem = findById(id);
        boolean wasBookmarked = problem.isBookmarked();
        boolean neededReview = problem.isNeedsReview();
        problem.patch(
                request.getPlatform(),
                request.getTitle(),
                request.getNumber(),
                request.getLink(),
                request.getTags(),
                request.getDifficulty(),
                request.getMemo(),
                request.getResult(),
                request.getNeedsReview(),
                request.getReviewedAt(),
                request.getReviewHistory(),
                request.getSolvedDates(),
                request.getLastSolvedAt(),
                request.getBookmarked()
        );
        if (Boolean.TRUE.equals(request.getNeedsReview())) {
            problem.markReviewRequired();
        }
        syncReviewState(problem);
        if (!wasBookmarked && problem.isBookmarked()) {
            searchActivityService.recordBookmarkEvent(problem);
        }
        if (!neededReview && problem.isNeedsReview()) {
            searchActivityService.recordMarkReviewEvent(problem);
        }
        return ProblemResponse.from(problem);
    }

    @Transactional
    public void deleteProblem(Long id) {
        Problem problem = findById(id);
        problemRepository.delete(problem);
    }

    private Problem findById(Long id) {
        Long userId = CurrentUserContext.getRequired().getId();
        return problemRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("문제를 찾을 수 없습니다. id=" + id));
    }

    private String resolveTitle(String title, String platform, String number) {
        if (title != null && !title.isBlank()) {
            return title;
        }
        if (platform != null && !platform.isBlank() && number != null && !number.isBlank()) {
            return platform + " " + number;
        }
        return number;
    }

    private void syncReviewState(Problem problem) {
        if (!problem.isNeedsReview()) {
            return;
        }

        LocalDate today = LocalDate.now();
        Review review = reviewRepository.findByProblemIdAndProblemUserId(problem.getId(), CurrentUserContext.getRequired().getId())
                .orElseGet(() -> reviewRepository.save(Review.builder()
                        .problem(problem)
                        .reviewCount(problem.getReviewHistory().size())
                        .lastReviewedDate(problem.getReviewedAt() != null ? problem.getReviewedAt() : today)
                        .nextReviewDate(today)
                        .build()));
        review.markPending(today);
    }
}
