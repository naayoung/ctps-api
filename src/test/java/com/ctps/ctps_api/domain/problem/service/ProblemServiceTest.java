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
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ProblemRepository;
import com.ctps.ctps_api.domain.review.repository.ReviewRepository;
import com.ctps.ctps_api.domain.search.service.SearchActivityService;
import com.ctps.ctps_api.global.security.AuthenticatedUser;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    private SearchActivityService searchActivityService;

    @Mock
    private ProblemMetadataService problemMetadataService;

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
        assertThat(response.getTitle()).isEqualTo("미로 탐색");
        assertThat(response.getTags()).containsExactly("그래프", "BFS");
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
}
