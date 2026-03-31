package com.ctps.ctps_api.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchResponse;
import com.ctps.ctps_api.domain.auth.entity.AuthProvider;
import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.ProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.search.DefaultSearchQueryPreprocessor;
import com.ctps.ctps_api.domain.search.dto.SearchRankingType;
import com.ctps.ctps_api.domain.search.dto.SearchItemSource;
import com.ctps.ctps_api.domain.search.entity.ProblemInteractionEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.ctps.ctps_api.global.security.AuthenticatedUser;
import com.ctps.ctps_api.global.security.CurrentUserContext;

class UnifiedSearchServiceTest {

    private final ProblemSearchService problemSearchService = Mockito.mock(ProblemSearchService.class);
    private final ExternalProblemSearchService externalProblemSearchService = Mockito.mock(ExternalProblemSearchService.class);
    private final com.ctps.ctps_api.domain.search.repository.ProblemInteractionEventRepository problemInteractionEventRepository =
            Mockito.mock(com.ctps.ctps_api.domain.search.repository.ProblemInteractionEventRepository.class);
    private final SearchResultDeduplicator searchResultDeduplicator = new SearchResultDeduplicator();
    private final UnifiedSearchResponseMapper responseMapper = new UnifiedSearchResponseMapper();
    private final SearchTypeCanonicalizer searchTypeCanonicalizer = new SearchTypeCanonicalizer();
    private final SearchCandidateCollector searchCandidateCollector = new SearchCandidateCollector(
            problemSearchService,
            externalProblemSearchService,
            responseMapper,
            searchResultDeduplicator,
            searchTypeCanonicalizer
    );
    private final UserPreferenceAnalyzer userPreferenceAnalyzer = new UserPreferenceAnalyzer(
            problemInteractionEventRepository,
            searchTypeCanonicalizer,
            Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneId.of("Asia/Seoul"))
    );

    private final UnifiedSearchService unifiedSearchService = new UnifiedSearchService(
            problemSearchService,
            externalProblemSearchService,
            new DefaultSearchQueryPreprocessor(),
            searchCandidateCollector,
            new UnifiedSearchRankingService(searchResultDeduplicator),
            userPreferenceAnalyzer,
            new SearchResultAssembler()
    );

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    @DisplayName("관련도순에서는 exact 내부 결과가 외부 결과보다 우선 노출된다")
    void search_prioritizesExactInternalResultsOnRelevance() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "그래프");
        setField(request, "page", 0);
        setField(request, "size", 3);

        given(problemSearchService.search(Mockito.any())).willReturn(ProblemSearchResponse.builder()
                .content(List.of(
                        internal("internal-1", "그래프 기초"),
                        internal("internal-2", "그래프 응용"),
                        internal("internal-3", "그래프 심화")
                ))
                .page(0)
                .size(9)
                .totalElements(3)
                .totalPages(1)
                .hasNext(false)
                .build());

        given(externalProblemSearchService.search(Mockito.any())).willReturn(ExternalProblemSearchResponse.builder()
                .content(List.of(
                        external("external-1", "그래프 추천"),
                        external("external-2", "그래프 추가")
                ))
                .page(0)
                .size(9)
                .totalElements(2)
                .totalPages(1)
                .hasNext(false)
                .failedProviders(List.of())
                .build());

        var response = unifiedSearchService.search(request);

        assertThat(response.getItems()).hasSize(3);
        assertThat(response.getItems().get(0).getTitle()).contains("그래프");
        assertThat(response.getExternalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("외부 제공자 실패 경고를 통합 검색 응답에 그대로 포함한다")
    void search_includesExternalWarning() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "dp");

        given(problemSearchService.search(Mockito.any())).willReturn(ProblemSearchResponse.builder()
                .content(List.of())
                .page(0)
                .size(30)
                .totalElements(0)
                .totalPages(0)
                .hasNext(false)
                .build());

        given(externalProblemSearchService.search(Mockito.any())).willReturn(ExternalProblemSearchResponse.builder()
                .content(List.of())
                .page(0)
                .size(30)
                .totalElements(0)
                .totalPages(0)
                .hasNext(false)
                .failedProviders(List.of("solved.ac"))
                .warningMessage("일부 외부 검색 제공자 응답이 지연되거나 실패해 결과가 일부만 표시될 수 있습니다.")
                .build());

        var response = unifiedSearchService.search(request);

        assertThat(response.getFailedExternalProviders()).containsExactly("solved.ac");
        assertThat(response.getExternalWarning()).isNotBlank();
    }

    @Test
    @DisplayName("제목 일치 결과가 적으면 같은 키워드 태그가 붙은 내부 문제도 첫 페이지에 포함한다")
    void search_keepsTagMatchedInternalItemsNearTitleMatches() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "사칙연산");
        setField(request, "page", 0);
        setField(request, "size", 5);

        given(problemSearchService.search(Mockito.any())).willReturn(ProblemSearchResponse.builder()
                .content(List.of(
                        internal("internal-title", "사칙연산"),
                        internal("internal-tag", "수식 처리", List.of("사칙연산", "구현"))
                ))
                .page(0)
                .size(15)
                .totalElements(2)
                .totalPages(1)
                .hasNext(false)
                .build());

        given(externalProblemSearchService.search(Mockito.any())).willReturn(ExternalProblemSearchResponse.builder()
                .content(List.of(
                        external("external-1", "연산자 끼워넣기"),
                        external("external-2", "계산기 구현")
                ))
                .page(0)
                .size(15)
                .totalElements(2)
                .totalPages(1)
                .hasNext(false)
                .failedProviders(List.of())
                .build());

        var response = unifiedSearchService.search(request);

        assertThat(response.getItems()).extracting(item -> item.getId())
                .startsWith("internal-title", "internal-tag");
    }

    @Test
    @DisplayName("exact 결과가 15개 이상이면 exact 결과만 상위 15개 반환한다")
    void search_returnsTop15ExactMatchesWhenExactIsEnough() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "포도주");
        setField(request, "size", 15);

        List<ProblemSearchItemResponse> exactItems = IntStream.range(0, 20)
                .mapToObj(index -> internal("exact-" + index, "포도주 문제 " + index, List.of("BFS")))
                .toList();

        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && "포도주".equals(argument.getKeyword()))))
                .willReturn(problemResponse(exactItems));
        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && !"포도주".equals(argument.getKeyword()))))
                .willReturn(problemResponse(List.of()));
        given(externalProblemSearchService.search(Mockito.any())).willReturn(externalResponse(List.of()));

        var response = unifiedSearchService.search(request);

        assertThat(response.getItems()).hasSize(15);
        assertThat(response.getItems()).allMatch(item -> item.getRankingType() == SearchRankingType.EXACT);
    }

    @Test
    @DisplayName("exact 3개 partial 2개 fallback 10개를 합쳐 총 15개를 exact, partial, fallback 순으로 반환한다")
    void search_fillsWithPartialAndFallbackUpTo15() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "포도주");
        setField(request, "size", 15);

        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && "포도주".equals(argument.getKeyword()))))
                .willReturn(problemResponse(List.of(
                        internal("exact-1", "포도주 시음"),
                        internal("exact-2", "포도주 창고"),
                        internal("exact-3", "포도주 분류")
                )));
        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && "포도".equals(argument.getKeyword()))))
                .willReturn(problemResponse(List.of(
                        internal("partial-1", "포도 수확", List.of("BFS")),
                        internal("partial-2", "포도 상자", List.of("그래프"))
                )));
        given(problemSearchService.search(Mockito.argThat(argument ->
                argument != null && argument.getKeyword() == null && argument.getTags().contains("BFS"))))
                .willReturn(problemResponse(IntStream.range(0, 10)
                        .mapToObj(index -> internal("fallback-" + index, "추천 문제 " + index, List.of("BFS", "그래프")))
                        .toList()));
        given(problemSearchService.search(Mockito.argThat(argument ->
                argument != null && argument.getKeyword() == null && !argument.getTags().contains("BFS"))))
                .willReturn(problemResponse(List.of()));
        given(externalProblemSearchService.search(Mockito.any())).willReturn(externalResponse(List.of()));

        var response = unifiedSearchService.search(request);

        assertThat(response.getItems()).hasSize(15);
        assertThat(response.getItems().subList(0, 3)).allMatch(item -> item.getRankingType() == SearchRankingType.EXACT);
        assertThat(response.getItems().subList(3, 5)).allMatch(item -> item.getRankingType() == SearchRankingType.PARTIAL);
        assertThat(response.getItems().subList(5, 15))
                .allMatch(item -> item.getRankingType() == SearchRankingType.RECOMMENDED_BY_TAG
                        || item.getRankingType() == SearchRankingType.RECOMMENDED_BY_USER_PREFERENCE);
    }

    @Test
    @DisplayName("'포도' 검색에서 15개 미만이면 연관 태그 fallback을 추가하고 debug 분포를 반환한다")
    void search_addsTagFallbackAndDebugInfoForPodoQuery() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "포도");
        setField(request, "size", 15);
        setField(request, "debug", true);

        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && "포도".equals(argument.getKeyword()))))
                .willReturn(problemResponse(List.of(
                        internal("exact-1", "건포도 선별", List.of("BFS", "그래프")),
                        internal("exact-2", "포도 창고", List.of("BFS")),
                        internal("exact-3", "청포도 마을", List.of("그래프", "BFS"))
                )));
        given(problemSearchService.search(Mockito.argThat(argument ->
                argument != null && argument.getKeyword() == null && argument.getTags().contains("BFS"))))
                .willReturn(problemResponse(List.of(
                        internal("fallback-1", "미로 탐색", List.of("BFS")),
                        internal("fallback-2", "섬 연결", List.of("BFS", "그래프")),
                        internal("fallback-3", "거리 계산", List.of("BFS", "그래프"))
                )));
        given(problemSearchService.search(Mockito.argThat(argument ->
                argument != null && argument.getKeyword() == null && !argument.getTags().contains("BFS"))))
                .willReturn(problemResponse(List.of(
                        internal("fallback-4", "그래프 순회", List.of("그래프"))
                )));
        given(externalProblemSearchService.search(Mockito.any())).willReturn(externalResponse(List.of()));

        var response = unifiedSearchService.search(request);

        assertThat(response.getItems()).hasSize(7);
        assertThat(response.getItems().stream()
                .filter(item -> item.getRankingType() == SearchRankingType.RECOMMENDED_BY_TAG))
                .isNotEmpty();
        assertThat(response.getItems().stream()
                .filter(item -> item.getRankingType() == SearchRankingType.RECOMMENDED_BY_TAG)
                .flatMap(item -> item.getMatchedTags().stream()))
                .contains("bfs");
        assertThat(response.getDebug()).isNotNull();
        assertThat(response.getDebug().getExactCandidatesCount()).isEqualTo(3);
        assertThat(response.getDebug().getPartialCandidatesCount()).isEqualTo(0);
        assertThat(response.getDebug().getFallbackCandidatesCount()).isGreaterThanOrEqualTo(4);
        assertThat(response.getDebug().getDeduplicatedCount()).isEqualTo(7);
        assertThat(response.getDebug().getRankingTypeCounts())
                .containsEntry(SearchRankingType.EXACT, 3L)
                .containsKey(SearchRankingType.RECOMMENDED_BY_TAG);
    }

    @Test
    @DisplayName("exact이 없고 partial만 존재하면 partial을 우선 반환하고 부족분은 fallback으로 채운다")
    void search_prefersPartialWhenExactIsMissing() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "포도주");
        setField(request, "size", 15);

        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && "포도주".equals(argument.getKeyword()))))
                .willReturn(problemResponse(List.of()));
        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && "포도".equals(argument.getKeyword()))))
                .willReturn(problemResponse(IntStream.range(0, 7)
                        .mapToObj(index -> internal("partial-" + index, "포도 문제 " + index, List.of("그래프")))
                        .toList()));
        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && argument.getKeyword() == null)))
                .willReturn(problemResponse(IntStream.range(0, 8)
                        .mapToObj(index -> internal("fallback-" + index, "후보 문제 " + index, List.of("그래프")))
                        .toList()));
        given(externalProblemSearchService.search(Mockito.any())).willReturn(externalResponse(List.of()));

        var response = unifiedSearchService.search(request);

        assertThat(response.getItems()).hasSize(15);
        assertThat(response.getItems().subList(0, 7)).allMatch(item -> item.getRankingType() == SearchRankingType.PARTIAL);
    }

    @Test
    @DisplayName("같은 문제가 여러 단계에서 잡혀도 최종 결과에는 한 번만 포함된다")
    void search_deduplicatesAcrossStages() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "포도주");
        setField(request, "size", 15);

        ProblemSearchItemResponse duplicate = internal("dup-1", "포도주 포도", List.of("BFS"));
        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && "포도주".equals(argument.getKeyword()))))
                .willReturn(problemResponse(List.of(duplicate)));
        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && "포도".equals(argument.getKeyword()))))
                .willReturn(problemResponse(List.of(duplicate, internal("partial-1", "포도 문제", List.of("BFS")))));
        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && argument.getKeyword() == null)))
                .willReturn(problemResponse(List.of(duplicate, internal("fallback-1", "추천 문제", List.of("BFS")))));
        given(externalProblemSearchService.search(Mockito.any())).willReturn(externalResponse(List.of()));

        var response = unifiedSearchService.search(request);

        assertThat(response.getItems().stream().filter(item -> item.getId().equals("dup-1")).count()).isEqualTo(1);
        assertThat(response.getItems().stream()
                .filter(item -> item.getId().equals("dup-1"))
                .findFirst())
                .get()
                .extracting(item -> item.getRankingType())
                .isEqualTo(SearchRankingType.EXACT);
        assertThat(response.getItems().stream().filter(item -> item.getId().equals("fallback-1")).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("사용자 선호 태그가 fallback 순위에 반영되고 debug 모드에서는 score breakdown을 포함한다")
    void search_appliesUserPreferenceAndIncludesScoreBreakdownInDebug() throws Exception {
        CurrentUserContext.set(AuthenticatedUser.builder()
                .id(7L)
                .username("tester")
                .displayName("테스터")
                .build());
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "포도주");
        setField(request, "size", 15);
        setField(request, "debug", true);

        LocalDateTime now = LocalDateTime.of(2026, 4, 1, 9, 0);
        given(problemInteractionEventRepository.findAllByUserIdOrderByCreatedAtDesc(Mockito.anyLong()))
                .willReturn(List.of(
                        interaction("1", List.of("BFS"), Problem.Difficulty.medium, now.minusDays(3)),
                        interaction("2", List.of("BFS"), Problem.Difficulty.medium, now.minusDays(7)),
                        interaction("3", List.of("DP"), Problem.Difficulty.hard, now.minusDays(20))
                ));

        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && "포도주".equals(argument.getKeyword()))))
                .willReturn(problemResponse(List.of()));
        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && "포도".equals(argument.getKeyword()))))
                .willReturn(problemResponse(List.of()));
        given(problemSearchService.search(Mockito.argThat(argument -> argument != null && argument.getKeyword() == null)))
                .willReturn(problemResponse(List.of(
                        internal("pref-1", "BFS 추천", List.of("BFS")),
                        internal("pref-2", "DP 추천", List.of("DP"))
                )));
        given(externalProblemSearchService.search(Mockito.any())).willReturn(externalResponse(List.of()));

        var response = unifiedSearchService.search(request);

        assertThat(response.getItems()).isNotEmpty();
        assertThat(response.getItems().get(0).getId()).isEqualTo("pref-1");
        assertThat(response.getItems().get(0).getScoreBreakdown()).isNotNull();
        assertThat(response.getItems().get(0).getScoreBreakdown().getUserPreferenceScore()).isGreaterThan(0);
        assertThat(response.getDebug()).isNotNull();
        assertThat(response.getDebug().getFallbackCandidatesCount()).isGreaterThan(0);
    }

    private ProblemSearchItemResponse internal(String id, String title) {
        return internal(id, title, List.of("그래프"));
    }

    private ProblemSearchItemResponse internal(String id, String title, List<String> tags) {
        return ProblemSearchItemResponse.builder()
                .id(id)
                .title(title)
                .platform("백준")
                .problemNumber(id)
                .difficulty(Problem.Difficulty.medium)
                .tags(tags)
                .result(Problem.Result.success)
                .needsReview(false)
                .bookmarked(false)
                .memoSummary("내부 메모")
                .build();
    }

    private ExternalProblemSearchItemResponse external(String id, String title) {
        return ExternalProblemSearchItemResponse.builder()
                .id(id)
                .providerKey("solvedac")
                .providerLabel("solved.ac")
                .title(title)
                .platform("백준")
                .problemNumber(id)
                .difficulty(Problem.Difficulty.medium)
                .tags(List.of("그래프"))
                .summary("외부 추천")
                .recommendationReason("추천 이유")
                .externalUrl("https://example.com/" + id)
                .providerNormalizedScore(1.0)
                .build();
    }

    private ProblemSearchResponse problemResponse(List<ProblemSearchItemResponse> items) {
        return ProblemSearchResponse.builder()
                .content(items)
                .page(0)
                .size(Math.max(items.size(), 1))
                .totalElements(items.size())
                .totalPages(items.isEmpty() ? 0 : 1)
                .hasNext(false)
                .build();
    }

    private ExternalProblemSearchResponse externalResponse(List<ExternalProblemSearchItemResponse> items) {
        return ExternalProblemSearchResponse.builder()
                .content(items)
                .page(0)
                .size(Math.max(items.size(), 1))
                .totalElements(items.size())
                .totalPages(items.isEmpty() ? 0 : 1)
                .hasNext(false)
                .failedProviders(List.of())
                .build();
    }

    private ProblemInteractionEvent interaction(
            String problemRef,
            List<String> tags,
            Problem.Difficulty difficulty,
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
                .eventType(ProblemInteractionEvent.EventType.DETAIL_VIEW)
                .createdAt(createdAt)
                .build();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
