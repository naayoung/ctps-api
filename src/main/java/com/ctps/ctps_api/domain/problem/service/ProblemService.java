package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.auth.repository.UserRepository;
import com.ctps.ctps_api.domain.problem.dto.ProblemCreateRequest;
import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResolveRequest;
import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResponse;
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
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final SearchActivityService searchActivityService;
    private final ProblemMetadataService problemMetadataService;

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
        if (problem.isBookmarked()) {
            problem.removeFromReviewQueue();
        }

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
        if (problem.isBookmarked()) {
            problem.removeFromReviewQueue();
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

    private void syncReviewState(Problem problem) {
        if (!problem.isNeedsReview() || problem.isBookmarked()) {
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
