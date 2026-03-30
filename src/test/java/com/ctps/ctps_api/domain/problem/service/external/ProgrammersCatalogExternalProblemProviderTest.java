package com.ctps.ctps_api.domain.problem.service.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.entity.ProgrammersProblemCatalog;
import com.ctps.ctps_api.domain.problem.repository.ProgrammersProblemCatalogRepository;
import com.ctps.ctps_api.domain.problem.service.search.SearchIntentAnalyzer;
import com.ctps.ctps_api.domain.search.service.SearchTypeCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProgrammersCatalogExternalProblemProviderTest {

    private final ProgrammersProblemCatalogRepository repository = Mockito.mock(ProgrammersProblemCatalogRepository.class);
    private final ProgrammersCatalogExternalProblemProvider provider = new ProgrammersCatalogExternalProblemProvider(
            repository,
            new ObjectMapper(),
            new SearchIntentAnalyzer(),
            new SearchTypeCanonicalizer()
    );

    @Test
    @DisplayName("태그-only keyword는 프로그래머스 태그 매칭 fallback으로 검색한다")
    void search_matchesInferredTagOnlyKeyword() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "DFS");

        ProgrammersProblemCatalog dfsItem = catalog(
                "programmers-1",
                "네트워크",
                "1",
                "medium",
                "[\"깊이 우선 탐색\",\"그래프\"]"
        );
        ProgrammersProblemCatalog nonMatchingItem = catalog(
                "programmers-2",
                "문자열 정리",
                "2",
                "easy",
                "[\"구현\"]"
        );
        given(repository.findAll()).willReturn(List.of(dfsItem, nonMatchingItem));

        var result = provider.search(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("programmers-1");
        assertThat(result.get(0).getTags()).contains("깊이 우선 탐색");
    }

    @Test
    @DisplayName("프로그램머스 검색 결과는 매칭 강도가 높은 문제를 먼저 노출한다")
    void search_prioritizesStrongerMatchesBeforeLimit() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "타겟");

        ProgrammersProblemCatalog weakMatch = catalog(
                "programmers-weak",
                "타겟 연습 문제",
                "100",
                "medium",
                "[\"깊이 우선 탐색\"]"
        );
        ProgrammersProblemCatalog strongMatch = catalog(
                "programmers-strong",
                "타겟 넘버",
                "101",
                "medium",
                "[\"깊이 우선 탐색\",\"DFS\"]"
        );
        given(repository.findAll()).willReturn(List.of(weakMatch, strongMatch));

        var result = provider.search(request);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("programmers-strong");
        assertThat(result.get(1).getId()).isEqualTo("programmers-weak");
    }

    private ProgrammersProblemCatalog catalog(
            String externalId,
            String title,
            String problemNumber,
            String difficulty,
            String tagsJson
    ) {
        return ProgrammersProblemCatalog.builder()
                .externalId(externalId)
                .title(title)
                .problemNumber(problemNumber)
                .difficulty(difficulty)
                .tagsJson(tagsJson)
                .externalUrl("https://school.programmers.co.kr/learn/courses/30/lessons/" + problemNumber)
                .recommendationReason("태그 기반 추천")
                .sourceUpdatedAt(LocalDateTime.now())
                .ingestedAt(LocalDateTime.now())
                .build();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
