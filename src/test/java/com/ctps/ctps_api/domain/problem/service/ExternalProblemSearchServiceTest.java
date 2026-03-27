package com.ctps.ctps_api.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.search.DefaultExternalProblemProviderScoreNormalizer;
import com.ctps.ctps_api.domain.problem.service.search.DefaultProblemSearchScorer;
import com.ctps.ctps_api.domain.problem.service.search.DefaultSearchQueryPreprocessor;
import com.ctps.ctps_api.domain.problem.service.search.GenericExternalProblemProviderScoreResolver;
import com.ctps.ctps_api.domain.problem.service.search.LeetCodeProviderScoreResolver;
import com.ctps.ctps_api.domain.problem.service.search.NoopProblemPersonalizationScorer;
import com.ctps.ctps_api.domain.problem.service.search.ProgrammersProviderScoreResolver;
import com.ctps.ctps_api.domain.problem.service.search.SolvedAcProviderScoreResolver;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExternalProblemSearchServiceTest {

    private final ExternalProblemProvider providerA = Mockito.mock(ExternalProblemProvider.class);
    private final ExternalProblemProvider providerB = Mockito.mock(ExternalProblemProvider.class);
    private final ExternalProblemCacheService cacheService = Mockito.mock(ExternalProblemCacheService.class);
    private final ExternalSearchMetricsService metricsService = Mockito.mock(ExternalSearchMetricsService.class);
    private final ExternalProblemQueryKeyGenerator queryKeyGenerator = new ExternalProblemQueryKeyGenerator();

    private final ExternalProblemSearchService service = new ExternalProblemSearchService(
            List.of(providerA, providerB),
            cacheService,
            metricsService,
            queryKeyGenerator,
            new DefaultProblemSearchScorer(new NoopProblemPersonalizationScorer()),
            new DefaultSearchQueryPreprocessor(),
            List.of(
                    new SolvedAcProviderScoreResolver(),
                    new LeetCodeProviderScoreResolver(),
                    new ProgrammersProviderScoreResolver(),
                    new GenericExternalProblemProviderScoreResolver()
            ),
            new DefaultExternalProblemProviderScoreNormalizer()
    );

    @Test
    @DisplayName("중복 외부 문제는 relevance score와 정보 richness 기준으로 하나만 남긴다")
    void search_deduplicatesAndSortsByRelevance() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "그래프");
        setField(request, "tags", List.of("그래프"));
        setField(request, "page", 0);
        setField(request, "size", 10);

        ExternalProblemSearchItemResponse weakDuplicate = ExternalProblemSearchItemResponse.builder()
                .id("provider-a-2178")
                .title("미로")
                .platform("백준")
                .problemNumber("2178")
                .difficulty(Problem.Difficulty.medium)
                .tags(List.of())
                .externalUrl("https://example.com/a")
                .recommendationReason("")
                .solved(false)
                .build();

        ExternalProblemSearchItemResponse strongDuplicate = ExternalProblemSearchItemResponse.builder()
                .id("provider-b-2178")
                .title("그래프 미로 탐색")
                .platform("백준")
                .problemNumber("2178")
                .difficulty(Problem.Difficulty.medium)
                .tags(List.of("그래프", "BFS"))
                .externalUrl("https://example.com/b")
                .recommendationReason("그래프 탐색 대표 문제")
                .solved(false)
                .build();

        ExternalProblemSearchItemResponse anotherItem = ExternalProblemSearchItemResponse.builder()
                .id("provider-b-1260")
                .title("DFS와 BFS")
                .platform("백준")
                .problemNumber("1260")
                .difficulty(Problem.Difficulty.medium)
                .tags(List.of("그래프"))
                .externalUrl("https://example.com/c")
                .recommendationReason("탐색 입문 문제")
                .solved(false)
                .build();

        given(cacheService.findValidCache(anyString(), anyString())).willReturn(null);
        given(providerA.search(any())).willReturn(List.of(weakDuplicate));
        given(providerB.search(any())).willReturn(List.of(strongDuplicate, anotherItem));

        ExternalProblemSearchResponse response = service.search(request);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getId()).isEqualTo("provider-b-2178");
        assertThat(response.getContent().get(0).getRelevanceScore()).isNotNull();
        assertThat(response.getContent().get(0).getRelevanceScore())
                .isGreaterThanOrEqualTo(response.getContent().get(1).getRelevanceScore());
        then(cacheService).should(atLeastOnce()).save(anyString(), anyString(), anyList(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("provider 실패 시 빈 리스트로 계속 진행하고 실패 메트릭을 기록한다")
    void search_logsFailureAndContinues() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "dp");

        given(cacheService.findValidCache(anyString(), anyString())).willReturn(null);
        given(providerA.search(any())).willThrow(new IllegalStateException("provider down"));
        given(providerB.search(any())).willReturn(List.of());

        ExternalProblemSearchResponse response = service.search(request);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getFailedProviders()).containsExactly(providerA.getClass().getSimpleName());
        assertThat(response.getWarningMessage()).isNotBlank();
        then(metricsService).should().recordProviderFailure(providerA.getClass().getSimpleName());
        then(cacheService).should(atLeastOnce()).save(anyString(), anyString(), anyList(), anyLong(), anyInt());
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
