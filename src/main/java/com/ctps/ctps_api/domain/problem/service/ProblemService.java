package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.auth.repository.UserRepository;
import com.ctps.ctps_api.domain.problem.dto.ProblemCreateRequest;
import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResolveRequest;
import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResponse;
import com.ctps.ctps_api.domain.problem.dto.ProblemResponse;
import com.ctps.ctps_api.domain.problem.dto.ProblemSolveHistoryItemResponse;
import com.ctps.ctps_api.domain.problem.dto.ProblemSolveHistoryResponse;
import com.ctps.ctps_api.domain.problem.dto.ProblemUpdateRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.entity.ProblemSolveHistoryEntry;
import com.ctps.ctps_api.domain.problem.repository.ProblemRepository;
import com.ctps.ctps_api.domain.problem.repository.ProblemSolveHistoryRepository;
import com.ctps.ctps_api.domain.review.entity.Review;
import com.ctps.ctps_api.domain.review.repository.ReviewHistoryRepository;
import com.ctps.ctps_api.domain.review.repository.ReviewRepository;
import com.ctps.ctps_api.domain.review.service.ReviewDateAggregationHelper;
import com.ctps.ctps_api.domain.search.service.SearchActivityService;
import com.ctps.ctps_api.global.exception.NotFoundException;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import com.ctps.ctps_api.global.time.DateTimeSupport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewHistoryRepository reviewHistoryRepository;
    private final ProblemSolveHistoryRepository problemSolveHistoryRepository;
    private final ProblemActivityService problemActivityService;
    private final SearchActivityService searchActivityService;
    private final ProblemMetadataService problemMetadataService;
    private final Clock clock;

    @Transactional
    public ProblemResponse createProblem(ProblemCreateRequest request) {
        User user = userRepository.getReferenceById(CurrentUserContext.getRequired().getId());
        ProblemMetadataResponse metadata = resolveProblemMetadata(
                request.getPlatform(),
                request.getNumber(),
                request.getLink(),
                request.getTitle(),
                request.getTags()
        );
        String resolvedPlatform = firstText(request.getPlatform(), metadata == null ? null : metadata.getPlatform());
        String resolvedNumber = firstText(request.getNumber(), metadata == null ? null : metadata.getNumber());
        String resolvedLink = firstText(request.getLink(), metadata == null ? null : metadata.getLink());
        List<String> resolvedTags = hasTags(request.getTags())
                ? request.getTags()
                : metadata == null || metadata.getTags() == null ? List.of() : metadata.getTags();
        Problem problem = Problem.builder()
                .user(user)
                .platform(resolvedPlatform)
                .title(resolveTitle(firstText(request.getTitle(), metadata == null ? null : metadata.getTitle()), resolvedPlatform, resolvedNumber))
                .number(resolvedNumber)
                .link(resolvedLink)
                .tags(resolvedTags)
                .difficulty(resolveDifficulty(request.getDifficulty(), request.isBookmarked(), request.getResult()))
                .memo(request.getMemo())
                .result(request.getResult())
                .needsReview(request.isNeedsReview())
                .reviewedAt(request.getReviewedAt())
                .reviewHistory(request.getReviewHistory())
                .createdAt(DateTimeSupport.nowUtc(clock))
                .solvedDates(request.getSolvedDates())
                .solveHistory(resolveSolveHistory(request.getSolveHistory(), request.getSolvedDates()))
                .lastSolvedAt(request.getLastSolvedAt())
                .bookmarked(request.isBookmarked())
                .build();
        normalizeReviewEligibility(problem);

        Problem saved = problemRepository.save(problem);
        problemActivityService.recordSolveAttempts(
                saved,
                List.of(),
                saved.getSolveHistory(),
                request.getResult(),
                request.getMemo()
        );
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

    public ProblemSolveHistoryResponse getSolveHistory(Long id) {
        Problem problem = findById(id);
        Long userId = CurrentUserContext.getRequired().getId();

        List<ProblemSolveHistoryItemResponse> entries = new ArrayList<>(
                problemSolveHistoryRepository.findAllByProblemIdAndUserIdOrderBySolvedAtDesc(problem.getId(), userId)
                        .stream()
                        .map(ProblemSolveHistoryItemResponse::from)
                        .toList()
        );

        if (entries.isEmpty()) {
            if (problem.getSolveHistory() != null && !problem.getSolveHistory().isEmpty()) {
                entries = problem.getSolveHistory().stream()
                        .sorted(Comparator.reverseOrder())
                        .map(ProblemSolveHistoryItemResponse::fallback)
                        .toList();
            } else {
                entries = (problem.getSolvedDates() == null ? List.<LocalDate>of() : problem.getSolvedDates()).stream()
                        .sorted(Comparator.reverseOrder())
                        .map(ProblemSolveHistoryItemResponse::fallback)
                        .toList();
            }
        }

        return ProblemSolveHistoryResponse.builder()
                .problemId(problem.getId())
                .problemTitle(problem.getTitle())
                .totalSolveCount(entries.size())
                .entries(entries)
                .build();
    }

    @Transactional
    public ProblemResponse updateProblem(Long id, ProblemUpdateRequest request) {
        Problem problem = findById(id);
        boolean wasBookmarked = problem.isBookmarked();
        boolean neededReview = problem.isNeedsReview();
        List<LocalDateTime> previousSolveHistory = resolveSolveHistory(problem);
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
                request.getSolveHistory(),
                request.getLastSolvedAt(),
                request.getBookmarked()
        );
        if (Boolean.TRUE.equals(request.getBookmarked()) && request.getResult() == null) {
            problem.clearDifficulty();
        }
        if (Boolean.TRUE.equals(request.getNeedsReview())) {
            problem.markReviewRequired();
        }
        normalizeReviewEligibility(problem);
        problemActivityService.recordSolveAttempts(
                problem,
                previousSolveHistory,
                resolveSolveHistory(problem),
                request.getResult(),
                request.getMemo()
        );
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
        problemSolveHistoryRepository.deleteAllByProblemId(problem.getId());
        reviewHistoryRepository.deleteAllByProblemId(problem.getId());
        reviewRepository.findByProblemId(problem.getId()).ifPresent(reviewRepository::delete);
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

    private ProblemMetadataResponse resolveProblemMetadata(
            String platform,
            String number,
            String link,
            String title,
            List<String> tags
    ) {
        if (StringUtils.hasText(title) && hasTags(tags)) {
            return null;
        }

        if (!StringUtils.hasText(link) && (!StringUtils.hasText(platform) || !StringUtils.hasText(number))) {
            return null;
        }

        ProblemMetadataResolveRequest request = new ProblemMetadataResolveRequest();
        request.setPlatform(platform);
        request.setNumber(number);
        request.setLink(link);
        ProblemMetadataResponse response = problemMetadataService.resolve(request);
        return response != null && response.isMetadataFound() ? response : null;
    }

    private boolean hasTags(List<String> tags) {
        return tags != null && !tags.isEmpty();
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private Problem.Difficulty resolveDifficulty(Problem.Difficulty difficulty, boolean bookmarked, Problem.Result result) {
        if (bookmarked && result == null) {
            return null;
        }
        return difficulty;
    }

    private void syncReviewState(Problem problem) {
        Long userId = CurrentUserContext.getRequired().getId();

        if (!problem.isNeedsReview() || !canEnterReviewQueue(problem)) {
            reviewRepository.findByProblemIdAndProblemUserId(problem.getId(), userId)
                    .ifPresent(reviewRepository::delete);
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate latestSolvedDate = problem.getLastSolvedAt();
        int cycleReviewCount = countReviewsSinceLatestSolve(problem);
        LocalDate cycleLastReviewedDate = resolveCycleLastReviewedDate(problem, latestSolvedDate, today);

        Review review = reviewRepository.findByProblemIdAndProblemUserId(problem.getId(), userId)
                .orElseGet(() -> reviewRepository.save(Review.builder()
                        .problem(problem)
                        .reviewCount(cycleReviewCount)
                        .lastReviewedDate(cycleLastReviewedDate)
                        .nextReviewDate(today)
                        .build()));

        if (latestSolvedDate != null && review.getLastReviewedDate().isBefore(latestSolvedDate)) {
            review.resetCycle(latestSolvedDate);
        } else {
            review.markPending(today);
        }
    }

    private void normalizeReviewEligibility(Problem problem) {
        if (!canEnterReviewQueue(problem)) {
            problem.removeFromReviewQueue();
        }
    }

    private boolean canEnterReviewQueue(Problem problem) {
        return problem.getResult() != null
                || (problem.getSolvedDates() != null && !problem.getSolvedDates().isEmpty())
                || (problem.getSolveHistory() != null && !problem.getSolveHistory().isEmpty())
                || problem.getLastSolvedAt() != null;
    }

    private List<LocalDateTime> resolveSolveHistory(List<LocalDateTime> solveHistory, List<LocalDate> solvedDates) {
        if (solveHistory != null && !solveHistory.isEmpty()) {
            return solveHistory;
        }

        if (solvedDates == null || solvedDates.isEmpty()) {
            return List.of();
        }

        return solvedDates.stream()
                .map(LocalDate::atStartOfDay)
                .toList();
    }

    private List<LocalDateTime> resolveSolveHistory(Problem problem) {
        return resolveSolveHistory(problem.getSolveHistory(), problem.getSolvedDates());
    }

    private int countReviewsSinceLatestSolve(Problem problem) {
        LocalDate latestSolvedDate = problem.getLastSolvedAt();
        return (int) ReviewDateAggregationHelper.countDistinctDatesSince(problem.getReviewHistory(), latestSolvedDate);
    }

    private LocalDate resolveCycleLastReviewedDate(Problem problem, LocalDate latestSolvedDate, LocalDate fallbackDate) {
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
