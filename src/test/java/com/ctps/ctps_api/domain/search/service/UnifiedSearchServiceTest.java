package com.ctps.ctps_api.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.ProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.search.DefaultSearchQueryPreprocessor;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UnifiedSearchServiceTest {

    private final ProblemSearchService problemSearchService = Mockito.mock(ProblemSearchService.class);
    private final ExternalProblemSearchService externalProblemSearchService = Mockito.mock(ExternalProblemSearchService.class);

    private final UnifiedSearchService unifiedSearchService = new UnifiedSearchService(
            problemSearchService,
            externalProblemSearchService,
            new DefaultSearchQueryPreprocessor(),
            new UnifiedSearchResponseMapper(),
            new UnifiedSearchRankingService()
    );

    @Test
    @DisplayName("관련도순에서는 외부 결과가 내부 결과 사이에 섞여 첫 페이지에서 노출된다")
    void search_interleavesExternalResultsOnRelevance() throws Exception {
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
        assertThat(response.getItems())
                .extracting(item -> item.getSource().name())
                .containsExactly("INTERNAL", "INTERNAL", "EXTERNAL");
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

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
