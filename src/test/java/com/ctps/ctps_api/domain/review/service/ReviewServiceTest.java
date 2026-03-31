package com.ctps.ctps_api.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ctps.ctps_api.domain.auth.entity.AuthProvider;
import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ProblemRepository;
import com.ctps.ctps_api.domain.problem.service.ProblemActivityService;
import com.ctps.ctps_api.domain.review.dto.ReviewCheckRequest;
import com.ctps.ctps_api.domain.review.dto.ReviewCheckResponse;
import com.ctps.ctps_api.domain.review.dto.ReviewHistoryResponse;
import com.ctps.ctps_api.domain.review.entity.Review;
import com.ctps.ctps_api.domain.review.entity.ReviewHistoryEntry;
import com.ctps.ctps_api.domain.review.policy.ConfigurableReviewSchedulePolicy;
import com.ctps.ctps_api.domain.review.policy.ReviewSchedulePolicy;
import com.ctps.ctps_api.domain.review.repository.ReviewHistoryRepository;
import com.ctps.ctps_api.domain.review.repository.ReviewRepository;
import com.ctps.ctps_api.global.security.AuthenticatedUser;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewHistoryRepository reviewHistoryRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ProblemActivityService problemActivityService;

    @Spy
    private ReviewSchedulePolicy reviewSchedulePolicy = new ConfigurableReviewSchedulePolicy("1,3,7,14,30");

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-03-31T13:15:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private ReviewService reviewService;

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    @DisplayName("하루에 한 번 복습하면 1회차로 계산한다")
    void checkReview_countsSingleReviewDayAsFirstRound() {
        LocalDate today = LocalDate.of(2026, 3, 31);
        Problem problem = createProblem(List.of(), null);

        givenAuthenticatedUser();
        given(problemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(problem));
        given(reviewRepository.findByProblemIdAndProblemUserId(1L, 1L)).willReturn(Optional.empty());
        given(reviewRepository.save(any(Review.class))).willAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            ReflectionTestUtils.setField(review, "id", 10L);
            return review;
        });
        given(reviewHistoryRepository.save(any(ReviewHistoryEntry.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReviewCheckResponse response = reviewService.checkReview(1L, null);

        assertThat(response.getReviewCount()).isEqualTo(1);
        assertThat(response.getLastReviewedDate()).isEqualTo(today);
        assertThat(response.getNextReviewDate()).isEqualTo(today.plusDays(1));
    }

    @Test
    @DisplayName("같은 날 세 번 복습해도 모두 1회차로 유지되고 다음 예정일도 한 번만 반영한다")
    void checkReview_sameDayRepeatsStayInSameRound() {
        LocalDate today = LocalDate.of(2026, 3, 31);
        Problem problem = createProblem(List.of(), null);

        givenAuthenticatedUser();
        given(problemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(problem));

        Review savedReview = Review.builder()
                .problem(problem)
                .reviewCount(0)
                .lastReviewedDate(today.minusDays(1))
                .nextReviewDate(today)
                .build();
        ReflectionTestUtils.setField(savedReview, "id", 10L);

        given(reviewRepository.findByProblemIdAndProblemUserId(1L, 1L))
                .willReturn(Optional.empty(), Optional.of(savedReview), Optional.of(savedReview));
        given(reviewRepository.save(any(Review.class))).willAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            ReflectionTestUtils.setField(review, "id", 10L);
            return review;
        });
        given(reviewHistoryRepository.save(any(ReviewHistoryEntry.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReviewCheckResponse first = reviewService.checkReview(1L, null);
        ReviewCheckResponse second = reviewService.checkReview(1L, null);
        ReviewCheckResponse third = reviewService.checkReview(1L, null);

        assertThat(first.getReviewCount()).isEqualTo(1);
        assertThat(second.getReviewCount()).isEqualTo(1);
        assertThat(third.getReviewCount()).isEqualTo(1);
        assertThat(first.getNextReviewDate()).isEqualTo(today.plusDays(1));
        assertThat(second.getNextReviewDate()).isEqualTo(today.plusDays(1));
        assertThat(third.getNextReviewDate()).isEqualTo(today.plusDays(1));

        ArgumentCaptor<ReviewHistoryEntry> entryCaptor = ArgumentCaptor.forClass(ReviewHistoryEntry.class);
        verify(reviewHistoryRepository, org.mockito.Mockito.times(3)).save(entryCaptor.capture());
        assertThat(entryCaptor.getAllValues())
                .extracting(ReviewHistoryEntry::getReviewCountAfterCheck)
                .containsExactly(1, 1, 1);
        assertThat(entryCaptor.getAllValues())
                .extracting(ReviewHistoryEntry::getNextReviewDate)
                .containsOnly(today.plusDays(1));
    }

    @Test
    @DisplayName("다음 날 다시 복습하면 2회차로 증가한다")
    void checkReview_nextDayReviewAdvancesRound() {
        LocalDate today = LocalDate.of(2026, 3, 31);
        LocalDate yesterday = today.minusDays(1);
        Problem problem = createProblem(List.of(yesterday), null);

        givenAuthenticatedUser();
        given(problemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(problem));

        Review review = Review.builder()
                .problem(problem)
                .reviewCount(1)
                .lastReviewedDate(yesterday)
                .nextReviewDate(today)
                .build();
        ReflectionTestUtils.setField(review, "id", 10L);

        given(reviewRepository.findByProblemIdAndProblemUserId(1L, 1L)).willReturn(Optional.of(review));
        given(reviewHistoryRepository.save(any(ReviewHistoryEntry.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReviewCheckResponse response = reviewService.checkReview(1L, null);

        assertThat(response.getReviewCount()).isEqualTo(2);
        assertThat(response.getNextReviewDate()).isEqualTo(today.plusDays(3));
    }

    @Test
    @DisplayName("밤 10시 이후 복습 완료 시 UTC 저장 기준으로 응답 시간이 유지된다")
    void checkReview_recordsLateNightCompletionInUtcAndPreservesSeoulDisplayTime() {
        LocalDate today = LocalDate.of(2026, 3, 31);
        Problem problem = createProblem(List.of(), null);
        ReviewCheckRequest request = new ReviewCheckRequest();
        ReflectionTestUtils.setField(request, "result", Problem.Result.partial);
        ReflectionTestUtils.setField(request, "memo", "늦은 밤 복습");

        givenAuthenticatedUser();
        given(problemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(problem));
        given(reviewRepository.findByProblemIdAndProblemUserId(1L, 1L)).willReturn(Optional.empty());
        given(reviewRepository.save(any(Review.class))).willAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            ReflectionTestUtils.setField(review, "id", 10L);
            return review;
        });
        given(reviewHistoryRepository.save(any(ReviewHistoryEntry.class))).willAnswer(invocation -> invocation.getArgument(0));

        reviewService.checkReview(1L, request);

        ArgumentCaptor<ReviewHistoryEntry> entryCaptor = ArgumentCaptor.forClass(ReviewHistoryEntry.class);
        verify(reviewHistoryRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 3, 31, 13, 15));
        verify(problemActivityService).recordReviewCompletion(
                eq(problem),
                eq(Problem.Result.partial),
                eq("늦은 밤 복습"),
                eq(LocalDateTime.of(2026, 3, 31, 13, 15))
        );

        given(reviewHistoryRepository.findAllByProblemIdAndUserIdOrderByReviewedAtDesc(1L, 1L))
                .willReturn(List.of(entryCaptor.getValue()));

        ReviewHistoryResponse response = reviewService.getReviewHistory(1L);

        assertThat(response.getEntries()).hasSize(1);
        assertThat(response.getEntries().get(0).getExecutedAtTimes())
                .containsExactly(OffsetDateTime.parse("2026-03-31T13:15:00Z"));
        assertThat(response.getEntries().get(0).getExecutedAtTimes().get(0)
                .withOffsetSameInstant(ZoneOffset.ofHours(9))
                .toLocalTime())
                .isEqualTo(java.time.LocalTime.of(22, 15));
        assertThat(response.getEntries().get(0).getReviewedAt()).isEqualTo(today);
    }

    @Test
    @DisplayName("같은 날짜 로그가 여러 건이어도 복습 흐름에는 하루 1건만 표시한다")
    void getReviewHistory_groupsSameDayLogsIntoSingleRound() {
        LocalDate today = LocalDate.now();
        Problem problem = createProblem(List.of(today), null);
        Review review = Review.builder()
                .problem(problem)
                .reviewCount(1)
                .lastReviewedDate(today)
                .nextReviewDate(today.plusDays(1))
                .build();
        ReflectionTestUtils.setField(review, "id", 10L);

        User user = problem.getUser();
        ReviewHistoryEntry first = createHistoryEntry(review, problem, user, 100L, today, today.plusDays(1), 1, LocalDateTime.of(today, java.time.LocalTime.of(9, 0)));
        ReviewHistoryEntry second = createHistoryEntry(review, problem, user, 101L, today, today.plusDays(1), 1, LocalDateTime.of(today, java.time.LocalTime.of(13, 0)));
        ReviewHistoryEntry third = createHistoryEntry(review, problem, user, 102L, today, today.plusDays(1), 1, LocalDateTime.of(today, java.time.LocalTime.of(18, 30)));

        givenAuthenticatedUser();
        given(problemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(problem));
        given(reviewHistoryRepository.findAllByProblemIdAndUserIdOrderByReviewedAtDesc(1L, 1L))
                .willReturn(List.of(first, second, third));

        ReviewHistoryResponse response = reviewService.getReviewHistory(1L);

        assertThat(response.getTotalReviewCount()).isEqualTo(1);
        assertThat(response.getEntries()).hasSize(1);
        assertThat(response.getEntries().get(0).getReviewCountAfterCheck()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 날짜 로그가 여러 건이어도 실제 실행 로그는 누락되지 않는다")
    void getReviewHistory_preservesAllExecutionLogsWithinDayGroup() {
        LocalDate today = LocalDate.now();
        Problem problem = createProblem(List.of(today), null);
        Review review = Review.builder()
                .problem(problem)
                .reviewCount(1)
                .lastReviewedDate(today)
                .nextReviewDate(today.plusDays(1))
                .build();
        ReflectionTestUtils.setField(review, "id", 10L);

        User user = problem.getUser();
        ReviewHistoryEntry first = createHistoryEntry(review, problem, user, 100L, today, today.plusDays(1), 1, LocalDateTime.of(today, java.time.LocalTime.of(9, 0)));
        ReviewHistoryEntry second = createHistoryEntry(review, problem, user, 101L, today, today.plusDays(1), 1, LocalDateTime.of(today, java.time.LocalTime.of(13, 0)));
        ReviewHistoryEntry third = createHistoryEntry(review, problem, user, 102L, today, today.plusDays(1), 1, LocalDateTime.of(today, java.time.LocalTime.of(18, 30)));

        givenAuthenticatedUser();
        given(problemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(problem));
        given(reviewHistoryRepository.findAllByProblemIdAndUserIdOrderByReviewedAtDesc(1L, 1L))
                .willReturn(List.of(first, second, third));

        ReviewHistoryResponse response = reviewService.getReviewHistory(1L);

        assertThat(response.getEntries().get(0).getExecutionCount()).isEqualTo(3);
        assertThat(response.getEntries().get(0).getExecutedAtTimes()).hasSize(3);
        assertThat(response.getEntries().get(0).getExecutedAtTimes())
                .containsExactly(
                        OffsetDateTime.of(LocalDateTime.of(today, java.time.LocalTime.of(18, 30)), ZoneOffset.UTC),
                        OffsetDateTime.of(LocalDateTime.of(today, java.time.LocalTime.of(13, 0)), ZoneOffset.UTC),
                        OffsetDateTime.of(LocalDateTime.of(today, java.time.LocalTime.of(9, 0)), ZoneOffset.UTC)
                );
    }

    private void givenAuthenticatedUser() {
        CurrentUserContext.set(AuthenticatedUser.builder()
                .id(1L)
                .username("tester")
                .displayName("테스터")
                .build());
    }

    private Problem createProblem(List<LocalDate> reviewHistory, LocalDate lastSolvedAt) {
        User user = User.builder()
                .username("tester")
                .passwordHash("hashed")
                .displayName("테스터")
                .primaryAuthProvider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        Problem problem = Problem.builder()
                .user(user)
                .platform("백준")
                .title("미로 탐색")
                .number("2178")
                .link("https://www.acmicpc.net/problem/2178")
                .tags(List.of("그래프"))
                .difficulty(Problem.Difficulty.medium)
                .memo("메모")
                .result(Problem.Result.success)
                .needsReview(true)
                .reviewHistory(reviewHistory)
                .createdAt(LocalDateTime.now())
                .solvedDates(lastSolvedAt == null ? List.of() : List.of(lastSolvedAt))
                .solveHistory(List.of())
                .lastSolvedAt(lastSolvedAt)
                .bookmarked(false)
                .build();
        ReflectionTestUtils.setField(problem, "id", 1L);
        return problem;
    }

    private ReviewHistoryEntry createHistoryEntry(
            Review review,
            Problem problem,
            User user,
            Long id,
            LocalDate reviewedAt,
            LocalDate nextReviewDate,
            int reviewCountAfterCheck,
            LocalDateTime createdAt
    ) {
        ReviewHistoryEntry entry = ReviewHistoryEntry.builder()
                .review(review)
                .problem(problem)
                .user(user)
                .reviewCountAfterCheck(reviewCountAfterCheck)
                .intervalDays((int) java.time.temporal.ChronoUnit.DAYS.between(reviewedAt, nextReviewDate))
                .reviewedAt(reviewedAt)
                .nextReviewDate(nextReviewDate)
                .createdAt(createdAt)
                .build();
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }
}
