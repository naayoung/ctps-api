package com.ctps.ctps_api.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ctps.ctps_api.domain.auth.entity.AuthProvider;
import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.search.dto.FrequentSearchTypeItemResponse;
import com.ctps.ctps_api.domain.search.dto.SearchItemSource;
import com.ctps.ctps_api.domain.search.entity.ProblemInteractionEvent;
import com.ctps.ctps_api.domain.search.entity.SearchEvent;
import com.ctps.ctps_api.domain.search.repository.ProblemInteractionEventRepository;
import com.ctps.ctps_api.domain.search.repository.SearchEventRepository;
import com.ctps.ctps_api.global.security.AuthenticatedUser;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FrequentSearchTypeServiceTest {

    private final SearchEventRepository searchEventRepository = Mockito.mock(SearchEventRepository.class);
    private final ProblemInteractionEventRepository problemInteractionEventRepository =
            Mockito.mock(ProblemInteractionEventRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-27T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final SearchTypeCanonicalizer searchTypeCanonicalizer = new SearchTypeCanonicalizer();

    private final FrequentSearchTypeService frequentSearchTypeService = new FrequentSearchTypeService(
            searchEventRepository,
            problemInteractionEventRepository,
            searchTypeCanonicalizer,
            clock
    );

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    @DisplayName("최근 클릭과 조회, 보조 검색 신호를 합산해 상위 유형 3개를 반환한다")
    void getFrequentTypes_returnsTopRankedItems() {
        CurrentUserContext.set(AuthenticatedUser.builder()
                .id(7L)
                .username("tester")
                .displayName("테스터")
                .build());

        LocalDateTime now = LocalDateTime.now(clock);
        given(problemInteractionEventRepository.findAllByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                Mockito.eq(7L),
                Mockito.any(LocalDateTime.class)
        )).willReturn(List.of(
                interaction("101", List.of("그래프", "BFS"), Problem.Difficulty.medium,
                        ProblemInteractionEvent.EventType.SEARCH_CLICK, now.minusDays(2)),
                interaction("102", List.of("최단경로", "시뮬레이션"), Problem.Difficulty.medium,
                        ProblemInteractionEvent.EventType.DETAIL_VIEW, now.minusDays(6)),
                interaction("103", List.of("DP", "구현"), Problem.Difficulty.hard,
                        ProblemInteractionEvent.EventType.DETAIL_VIEW, now.minusDays(20)),
                interaction("104", List.of("graph"), Problem.Difficulty.medium,
                        ProblemInteractionEvent.EventType.MARK_REVIEW, now.minusDays(3))
        ));
        given(searchEventRepository.findAllByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                Mockito.eq(7L),
                Mockito.any(LocalDateTime.class)
        )).willReturn(List.of(
                search("실버 그래프 추천 문제", now.minusDays(1)),
                search("구현", now.minusDays(15))
        ));

        var response = frequentSearchTypeService.getFrequentTypes();

        assertThat(response.isHasEnoughData()).isTrue();
        assertThat(response.getItems()).hasSize(3);
        assertThat(response.getItems().get(0).getType()).isEqualTo(FrequentSearchTypeItemResponse.Type.TAG);
        assertThat(response.getItems().get(0).getLabel()).isEqualTo("그래프");
        assertThat(response.getItems().get(0).getScore()).isEqualTo(12.0);
        assertThat(response.getItems().get(0).getEvidenceCount()).isEqualTo(4);
        assertThat(response.getItems().get(1).getLabel()).isEqualTo("보통");
        assertThat(response.getItems().get(1).getScore()).isEqualTo(12.0);
        assertThat(response.getItems().get(2).getLabel()).isEqualTo("구현");
        assertThat(response.getItems().get(2).getScore()).isEqualTo(6.0);
        assertThat(response.getFocusedItems()).isNotEmpty();
        assertThat(response.getFocusedItems().get(0).getLabel()).isEqualTo("그래프");
        assertThat(response.getReviewNeededItems()).hasSize(2);
        assertThat(response.getReviewNeededItems().get(0).getLabel()).isEqualTo("그래프");
    }

    @Test
    @DisplayName("상호작용 데이터가 2건 미만이면 데이터를 충분하지 않다고 판단한다")
    void getFrequentTypes_returnsInsufficientWhenInteractionDataIsSparse() {
        CurrentUserContext.set(AuthenticatedUser.builder()
                .id(9L)
                .username("tester")
                .displayName("테스터")
                .build());

        LocalDateTime now = LocalDateTime.now(clock);
        given(problemInteractionEventRepository.findAllByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                Mockito.eq(9L),
                Mockito.any(LocalDateTime.class)
        )).willReturn(List.of(
                interaction("201", List.of("그래프"), Problem.Difficulty.medium,
                        ProblemInteractionEvent.EventType.SEARCH_CLICK, now.minusDays(1))
        ));
        given(searchEventRepository.findAllByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                Mockito.eq(9L),
                Mockito.any(LocalDateTime.class)
        )).willReturn(List.of(search("실버 그래프", now.minusDays(1))));

        var response = frequentSearchTypeService.getFrequentTypes();

        assertThat(response.isHasEnoughData()).isFalse();
        assertThat(response.getItems()).isEmpty();
    }

    private ProblemInteractionEvent interaction(
            String problemRef,
            List<String> tags,
            Problem.Difficulty difficulty,
            ProblemInteractionEvent.EventType eventType,
            LocalDateTime createdAt
    ) {
        return ProblemInteractionEvent.builder()
                .user(User.builder()
                        .username("tester")
                        .passwordHash("hash")
                        .displayName("테스터")
                        .createdAt(createdAt.minusDays(1))
                        .updatedAt(createdAt.minusDays(1))
                        .primaryAuthProvider(AuthProvider.LOCAL)
                        .build())
                .problemRef(problemRef)
                .source(SearchItemSource.INTERNAL)
                .platform("백준")
                .problemNumber(problemRef)
                .difficulty(difficulty)
                .tags(tags)
                .eventType(eventType)
                .createdAt(createdAt)
                .build();
    }

    private SearchEvent search(String query, LocalDateTime createdAt) {
        return SearchEvent.builder()
                .user(User.builder()
                        .username("tester")
                        .passwordHash("hash")
                        .displayName("테스터")
                        .createdAt(createdAt.minusDays(1))
                        .updatedAt(createdAt.minusDays(1))
                        .primaryAuthProvider(AuthProvider.LOCAL)
                        .build())
                .query(query)
                .createdAt(createdAt)
                .build();
    }
}
