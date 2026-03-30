package com.ctps.ctps_api.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExternalProblemQueryKeyGeneratorTest {

    private final ExternalProblemQueryKeyGenerator generator = new ExternalProblemQueryKeyGenerator();

    @Test
    @DisplayName("태그-only keyword는 text keyword 대신 canonical tag 기준으로 캐시 키를 만든다")
    void generate_usesResolvedSearchIntent() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "DFS");
        setField(request, "platform", List.of("백준"));

        String queryKey = generator.generate("solvedac", request);

        assertThat(queryKey).contains("version=v2");
        assertThat(queryKey).contains("|provider=solvedac");
        assertThat(queryKey).contains("|keyword=");
        assertThat(queryKey).contains("|tags=DFS");
        assertThat(queryKey).doesNotContain("|keyword=DFS");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
