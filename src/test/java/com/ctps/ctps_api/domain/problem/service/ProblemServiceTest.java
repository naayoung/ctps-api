package com.ctps.ctps_api.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.ctps.ctps_api.domain.auth.entity.AuthProvider;
import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.auth.repository.UserRepository;
import com.ctps.ctps_api.domain.problem.dto.ProblemCreateRequest;
import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResponse;
import com.ctps.ctps_api.domain.problem.dto.ProblemResponse;
import com.ctps.ctps_api.domain.problem.dto.ProblemSolveHistoryResponse;
import com.ctps.ctps_api.domain.problem.dto.ProblemUpdateRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.entity.ProblemSolveHistoryEntry;
import com.ctps.ctps_api.domain.problem.repository.ProblemRepository;
import com.ctps.ctps_api.domain.problem.repository.ProblemSolveHistoryRepository;
import com.ctps.ctps_api.domain.review.entity.Review;
import com.ctps.ctps_api.domain.review.repository.ReviewHistoryRepository;
import com.ctps.ctps_api.domain.review.repository.ReviewRepository;
import com.ctps.ctps_api.domain.search.service.SearchActivityService;
import com.ctps.ctps_api.global.security.AuthenticatedUser;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewHistoryRepository reviewHistoryRepository;

    @Mock
    private ProblemSolveHistoryRepository problemSolveHistoryRepository;

    @Mock
    private ProblemActivityService problemActivityService;

    @Mock
    private SearchActivityService searchActivityService;

    @Mock
    private ProblemMetadataService problemMetadataService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-03-31T13:15:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private ProblemService problemService;

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    @DisplayName("문제 생성 시 제목과 태그가 비어 있으면 링크 메타데이터로 보강한다")
    void createProblem_enrichesTitleAndTagsFromMetadata() {
        CurrentUserContext.set(AuthenticatedUser.builder()
                .id(1L)
                .username("tester")
                .displayName("테스터")
                .build());

        User user = User.builder()
                .username("tester")
                .passwordHash("hashed")
                .displayName("테스터")
                .primaryAuthProvider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        ProblemCreateRequest request = new ProblemCreateRequest();
        ReflectionTestUtils.setField(request, "platform", "백준");
        ReflectionTestUtils.setField(request, "title", "");
        ReflectionTestUtils.setField(request, "number", "2178");
        ReflectionTestUtils.setField(request, "link", "https://www.acmicpc.net/problem/2178");
        ReflectionTestUtils.setField(request, "tags", List.of());
        ReflectionTestUtils.setField(request, "difficulty", Problem.Difficulty.medium);
        ReflectionTestUtils.setField(request, "memo", "");
        ReflectionTestUtils.setField(request, "reviewHistory", List.of());
        ReflectionTestUtils.setField(request, "solvedDates", List.of());
        ReflectionTestUtils.setField(request, "solveHistory", List.of());
        ReflectionTestUtils.setField(request, "bookmarked", true);

        given(userRepository.getReferenceById(1L)).willReturn(user);
        given(problemMetadataService.resolve(any())).willReturn(ProblemMetadataResponse.builder()
                .platform("백준")
                .number("2178")
                .link("https://www.acmicpc.net/problem/2178")
                .title("미로 탐색")
                .tags(List.of("그래프", "BFS"))
                .metadataFound(true)
                .build());
        given(problemRepository.save(any(Problem.class))).willAnswer(invocation -> {
            Problem saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });

        ProblemResponse response = problemService.createProblem(request);

        ArgumentCaptor<Problem> problemCaptor = ArgumentCaptor.forClass(Problem.class);
        org.mockito.Mockito.verify(problemRepository).save(problemCaptor.capture());
        Problem saved = problemCaptor.getValue();

        assertThat(saved.getTitle()).isEqualTo("미로 탐색");
        assertThat(saved.getTags()).containsExactly("그래프", "BFS");
        assertThat(saved.getDifficulty()).isNull();
        assertThat(response.getTitle()).isEqualTo("미로 탐색");
        assertThat(response.getTags()).containsExactly("그래프", "BFS");
        assertThat(response.getDifficulty()).isNull();
    }

    @Test
    @DisplayName("문제 생성 시 직접 입력한 제목과 태그는 메타데이터보다 우선한다")
    void createProblem_preservesManualTitleAndTags() {
        CurrentUserContext.set(AuthenticatedUser.builder()
                .id(1L)
                .username("tester")
                .displayName("테스터")
                .build());

        User user = User.builder()
                .username("tester")
                .passwordHash("hashed")
                .displayName("테스터")
                .primaryAuthProvider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        ProblemCreateRequest request = new ProblemCreateRequest();
        ReflectionTestUtils.setField(request, "platform", "백준");
        ReflectionTestUtils.setField(request, "title", "직접 입력한 제목");
        ReflectionTestUtils.setField(request, "number", "2178");
        ReflectionTestUtils.setField(request, "link", "https://www.acmicpc.net/problem/2178");
        ReflectionTestUtils.setField(request, "tags", List.of("직접태그"));
        ReflectionTestUtils.setField(request, "difficulty", Problem.Difficulty.medium);
        ReflectionTestUtils.setField(request, "memo", "");
        ReflectionTestUtils.setField(request, "reviewHistory", List.of());
        ReflectionTestUtils.setField(request, "solvedDates", List.of());
        ReflectionTestUtils.setField(request, "solveHistory", List.of());
        ReflectionTestUtils.setField(request, "bookmarked", true);

        given(userRepository.getReferenceById(1L)).willReturn(user);
        given(problemRepository.save(any(Problem.class))).willAnswer(invocation -> {
            Problem saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        ProblemResponse response = problemService.createProblem(request);

        org.mockito.Mockito.verify(problemMetadataService, org.mockito.Mockito.never()).resolve(any());
        assertThat(response.getTitle()).isEqualTo("직접 입력한 제목");
        assertThat(response.getTags()).containsExactly("직접태그");
    }

    @Test
    @DisplayName("찜 전용 문제를 찜 해제해도 풀이 이력이 없으면 복습 필요로 돌아가지 않는다")
    void updateProblem_unbookmarkBookmarkOnlyProblem_keepsReviewDisabled() {
        CurrentUserContext.set(AuthenticatedUser.builder()
                .id(1L)
                .username("tester")
                .displayName("테스터")
                .build());

        Problem problem = Problem.builder()
                .platform("백준")
                .title("미로 탐색")
                .number("2178")
                .link("https://www.acmicpc.net/problem/2178")
                .tags(List.of("그래프"))
                .difficulty(Problem.Difficulty.medium)
                .memo("")
                .result(null)
                .needsReview(true)
                .reviewHistory(List.of())
                .createdAt(LocalDateTime.now())
                .solvedDates(List.of())
                .solveHistory(List.of())
                .lastSolvedAt(null)
                .bookmarked(true)
                .build();
        ReflectionTestUtils.setField(problem, "id", 10L);

        Review review = Review.builder()
                .problem(problem)
                .reviewCount(0)
                .lastReviewedDate(java.time.LocalDate.now())
                .nextReviewDate(java.time.LocalDate.now())
                .build();

        ProblemUpdateRequest request = new ProblemUpdateRequest();
        ReflectionTestUtils.setField(request, "bookmarked", false);

        given(problemRepository.findByIdAndUserId(10L, 1L)).willReturn(java.util.Optional.of(problem));
        given(reviewRepository.findByProblemIdAndProblemUserId(10L, 1L)).willReturn(java.util.Optional.of(review));

        ProblemResponse response = problemService.updateProblem(10L, request);

        assertThat(response.isBookmarked()).isFalse();
        assertThat(response.isNeedsReview()).isFalse();
        verify(reviewRepository).delete(review);
        verify(searchActivityService, never()).recordMarkReviewEvent(any());
    }

    @Test
    @DisplayName("기존 풀이 날짜만 있는 문제도 응답에서는 풀이 이력과 횟수로 보강된다")
    void getProblem_enrichesSolveHistoryFromSolvedDates() {
        CurrentUserContext.set(AuthenticatedUser.builder()
                .id(1L)
                .username("tester")
                .displayName("테스터")
                .build());

        Problem problem = Problem.builder()
                .platform("백준")
                .title("미로 탐색")
                .number("2178")
                .link("https://www.acmicpc.net/problem/2178")
                .tags(List.of("그래프"))
                .difficulty(Problem.Difficulty.medium)
                .memo("")
                .result(Problem.Result.success)
                .needsReview(false)
                .reviewHistory(List.of())
                .createdAt(LocalDateTime.now())
                .solvedDates(List.of(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 29)))
                .solveHistory(List.of())
                .lastSolvedAt(LocalDate.of(2026, 3, 29))
                .bookmarked(false)
                .build();
        ReflectionTestUtils.setField(problem, "id", 21L);

        given(problemRepository.findByIdAndUserId(21L, 1L)).willReturn(java.util.Optional.of(problem));

        ProblemResponse response = problemService.getProblem(21L);

        assertThat(response.getSolveCount()).isEqualTo(2);
        assertThat(response.getSolveHistory())
                .containsExactly(
                        OffsetDateTime.parse("2026-03-20T00:00:00+09:00"),
                        OffsetDateTime.parse("2026-03-29T00:00:00+09:00")
                );
    }

    @Test
    @DisplayName("같은 문제를 다시 풀면 복습 회차는 가장 최근 풀이일 기준으로 초기화된다")
    void updateProblem_resolvedAgain_resetsReviewCycle() {
        CurrentUserContext.set(AuthenticatedUser.builder()
                .id(1L)
                .username("tester")
                .displayName("테스터")
                .build());

        Problem problem = Problem.builder()
                .platform("백준")
                .title("미로 탐색")
                .number("2178")
                .link("https://www.acmicpc.net/problem/2178")
                .tags(List.of("그래프"))
                .difficulty(Problem.Difficulty.medium)
                .memo("기존 메모")
                .result(Problem.Result.success)
                .needsReview(true)
                .reviewedAt(LocalDate.of(2026, 3, 25))
                .reviewHistory(List.of(LocalDate.of(2026, 3, 22), LocalDate.of(2026, 3, 25)))
                .createdAt(LocalDateTime.now())
                .solvedDates(List.of(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 30)))
                .solveHistory(List.of(
                        LocalDate.of(2026, 3, 20).atStartOfDay(),
                        LocalDate.of(2026, 3, 30).atStartOfDay()
                ))
                .lastSolvedAt(LocalDate.of(2026, 3, 20))
                .bookmarked(false)
                .build();
        ReflectionTestUtils.setField(problem, "id", 30L);

        Review review = Review.builder()
                .problem(problem)
                .reviewCount(2)
                .lastReviewedDate(LocalDate.of(2026, 3, 25))
                .nextReviewDate(LocalDate.of(2026, 3, 28))
                .build();

        ProblemUpdateRequest request = new ProblemUpdateRequest();
        ReflectionTestUtils.setField(request, "memo", "다시 풀고 정리한 메모");
        ReflectionTestUtils.setField(request, "result", Problem.Result.success);
        ReflectionTestUtils.setField(request, "needsReview", true);
        ReflectionTestUtils.setField(request, "reviewHistory", List.of(LocalDate.of(2026, 3, 22), LocalDate.of(2026, 3, 25)));
        ReflectionTestUtils.setField(request, "solvedDates", List.of(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 30)));
        ReflectionTestUtils.setField(request, "solveHistory", List.of(
                LocalDate.of(2026, 3, 20).atStartOfDay(),
                LocalDate.of(2026, 3, 30).atStartOfDay()
        ));
        ReflectionTestUtils.setField(request, "lastSolvedAt", LocalDate.of(2026, 3, 30));

        given(problemRepository.findByIdAndUserId(30L, 1L)).willReturn(java.util.Optional.of(problem));
        given(reviewRepository.findByProblemIdAndProblemUserId(30L, 1L)).willReturn(java.util.Optional.of(review));

        problemService.updateProblem(30L, request);

        assertThat(review.getReviewCount()).isZero();
        assertThat(review.getLastReviewedDate()).isEqualTo(LocalDate.of(2026, 3, 30));
        assertThat(review.getNextReviewDate()).isEqualTo(LocalDate.of(2026, 3, 30));
    }

    @Test
    @DisplayName("북마크한 문제도 복습 필요 상태를 함께 유지할 수 있다")
    void updateProblem_bookmarkedProblem_canRemainInReviewQueue() {
        CurrentUserContext.set(AuthenticatedUser.builder()
                .id(1L)
                .username("tester")
                .displayName("테스터")
                .build());

        Problem problem = Problem.builder()
                .platform("백준")
                .title("미로 탐색")
                .number("2178")
                .link("https://www.acmicpc.net/problem/2178")
                .tags(List.of("그래프"))
                .difficulty(Problem.Difficulty.medium)
                .memo("복습이 필요한 문제")
                .result(Problem.Result.success)
                .needsReview(false)
                .reviewHistory(List.of())
                .createdAt(LocalDateTime.now())
                .solvedDates(List.of(LocalDate.of(2026, 3, 20)))
                .solveHistory(List.of(LocalDate.of(2026, 3, 20).atStartOfDay()))
                .lastSolvedAt(LocalDate.of(2026, 3, 20))
                .bookmarked(true)
                .build();
        ReflectionTestUtils.setField(problem, "id", 40L);

        ProblemUpdateRequest request = new ProblemUpdateRequest();
        ReflectionTestUtils.setField(request, "needsReview", true);
        ReflectionTestUtils.setField(request, "bookmarked", true);

        given(problemRepository.findByIdAndUserId(40L, 1L)).willReturn(java.util.Optional.of(problem));
        given(reviewRepository.findByProblemIdAndProblemUserId(40L, 1L)).willReturn(java.util.Optional.empty());
        given(reviewRepository.save(any(Review.class))).willAnswer(invocation -> invocation.getArgument(0));

        ProblemResponse response = problemService.updateProblem(40L, request);

        assertThat(response.isBookmarked()).isTrue();
        assertThat(response.isNeedsReview()).isTrue();
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("같은 문제를 다시 성공으로 기록하면 풀이 날짜와 메모가 회차별 이력으로 저장된다")
    void updateProblem_successSolve_savesSolveHistoryEntry() {
        CurrentUserContext.set(AuthenticatedUser.builder()
                .id(1L)
                .username("tester")
                .displayName("테스터")
                .build());

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
                .memo("예전 메모")
                .result(Problem.Result.success)
                .needsReview(true)
                .reviewHistory(List.of())
                .createdAt(LocalDateTime.now())
                .solvedDates(List.of(LocalDate.of(2026, 3, 20)))
                .solveHistory(List.of(LocalDate.of(2026, 3, 20).atStartOfDay()))
                .lastSolvedAt(LocalDate.of(2026, 3, 20))
                .bookmarked(false)
                .build();
        ReflectionTestUtils.setField(problem, "id", 50L);

        ProblemUpdateRequest request = new ProblemUpdateRequest();
        ReflectionTestUtils.setField(request, "memo", "다시 풀면서 남긴 메모");
        ReflectionTestUtils.setField(request, "result", Problem.Result.success);
        ReflectionTestUtils.setField(request, "needsReview", true);
        ReflectionTestUtils.setField(request, "reviewHistory", List.of());
        ReflectionTestUtils.setField(request, "solvedDates", List.of(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 30)));
        ReflectionTestUtils.setField(request, "solveHistory", List.of(
                LocalDate.of(2026, 3, 20).atStartOfDay(),
                LocalDate.of(2026, 3, 30).atTime(21, 15)
        ));
        ReflectionTestUtils.setField(request, "lastSolvedAt", LocalDate.of(2026, 3, 30));

        given(problemRepository.findByIdAndUserId(50L, 1L)).willReturn(java.util.Optional.of(problem));
        given(reviewRepository.findByProblemIdAndProblemUserId(50L, 1L)).willReturn(java.util.Optional.empty());
        given(reviewRepository.save(any(Review.class))).willAnswer(invocation -> invocation.getArgument(0));

        problemService.updateProblem(50L, request);

        ArgumentCaptor<List<LocalDateTime>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(problemActivityService).recordSolveAttempts(
                eq(problem),
                any(),
                historyCaptor.capture(),
                eq(Problem.Result.success),
                eq("다시 풀면서 남긴 메모")
        );
        assertThat(historyCaptor.getValue())
                .containsExactly(
                        LocalDate.of(2026, 3, 20).atStartOfDay(),
                        LocalDate.of(2026, 3, 30).atTime(21, 15)
                );
    }

    @Test
    @DisplayName("저장된 풀이 엔트리가 없으면 기존 solveHistory 데이터로 풀이 이력을 보여준다")
    void getSolveHistory_fallsBackToSolveHistoryField() {
        CurrentUserContext.set(AuthenticatedUser.builder()
                .id(1L)
                .username("tester")
                .displayName("테스터")
                .build());

        Problem problem = Problem.builder()
                .platform("백준")
                .title("미로 탐색")
                .number("2178")
                .link("https://www.acmicpc.net/problem/2178")
                .tags(List.of("그래프"))
                .difficulty(Problem.Difficulty.medium)
                .memo("")
                .result(Problem.Result.success)
                .needsReview(false)
                .reviewHistory(List.of())
                .createdAt(LocalDateTime.now())
                .solvedDates(List.of())
                .solveHistory(List.of(
                        LocalDate.of(2026, 3, 30).atTime(21, 15),
                        LocalDate.of(2026, 3, 20).atTime(9, 0)
                ))
                .lastSolvedAt(LocalDate.of(2026, 3, 30))
                .bookmarked(false)
                .build();
        ReflectionTestUtils.setField(problem, "id", 60L);

        given(problemRepository.findByIdAndUserId(60L, 1L)).willReturn(java.util.Optional.of(problem));
        given(problemSolveHistoryRepository.findAllByProblemIdAndUserIdOrderBySolvedAtDesc(60L, 1L)).willReturn(List.of());

        ProblemSolveHistoryResponse response = problemService.getSolveHistory(60L);

        assertThat(response.getTotalSolveCount()).isEqualTo(2);
        assertThat(response.getEntries()).hasSize(2);
        assertThat(response.getEntries().get(0).getSolvedAt()).isEqualTo(OffsetDateTime.parse("2026-03-30T21:15:00Z"));
        assertThat(response.getEntries().get(0).isMetadataFallback()).isTrue();
    }

    @Test
    @DisplayName("문제 삭제 시 풀이 시도 이력도 함께 먼저 삭제한다")
    void deleteProblem_deletesSolveAttemptHistoryBeforeProblem() {
        CurrentUserContext.set(AuthenticatedUser.builder()
                .id(1L)
                .username("tester")
                .displayName("테스터")
                .build());

        Problem problem = Problem.builder()
                .platform("프로그래머스")
                .title("타겟 넘버")
                .number("43165")
                .link("https://school.programmers.co.kr/learn/courses/30/lessons/43165")
                .tags(List.of("DFS/BFS"))
                .difficulty(Problem.Difficulty.medium)
                .memo("메모")
                .result(Problem.Result.success)
                .needsReview(false)
                .reviewHistory(List.of())
                .createdAt(LocalDateTime.now())
                .solvedDates(List.of(LocalDate.of(2026, 3, 20)))
                .solveHistory(List.of(LocalDate.of(2026, 3, 20).atStartOfDay()))
                .lastSolvedAt(LocalDate.of(2026, 3, 20))
                .bookmarked(false)
                .build();
        ReflectionTestUtils.setField(problem, "id", 43165L);

        given(problemRepository.findByIdAndUserId(43165L, 1L)).willReturn(java.util.Optional.of(problem));
        given(reviewRepository.findByProblemId(43165L)).willReturn(java.util.Optional.empty());

        problemService.deleteProblem(43165L);

        verify(problemSolveHistoryRepository).deleteAllByProblemId(43165L);
        verify(reviewHistoryRepository).deleteAllByProblemId(43165L);
        verify(problemRepository).delete(problem);
    }
}
