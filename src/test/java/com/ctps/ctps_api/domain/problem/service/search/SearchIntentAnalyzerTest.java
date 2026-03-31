package com.ctps.ctps_api.domain.problem.service.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchIntentAnalyzerTest {

    private final SearchIntentAnalyzer analyzer = new SearchIntentAnalyzer();

    @Test
    @DisplayName("태그 alias만 입력된 keyword는 tag-only 검색으로 판단한다")
    void detectsTagOnlyKeyword() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "DFS");

        assertThat(analyzer.isTagOnlySearch(request)).isTrue();
        assertThat(analyzer.resolveKeywordText(request)).isBlank();
        assertThat(analyzer.resolveCanonicalTags(request)).containsExactly("DFS");
    }

    @Test
    @DisplayName("여러 태그 alias를 공백으로 나열한 keyword도 canonical tag로 정리한다")
    void resolvesMultipleTagsFromKeyword() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "DFS BFS");

        assertThat(analyzer.isTagOnlySearch(request)).isTrue();
        assertThat(analyzer.resolveCanonicalTags(request)).containsExactly("DFS", "BFS");
    }

    @Test
    @DisplayName("일반 텍스트가 섞인 keyword는 text 검색으로 유지한다")
    void keepsFreeTextKeyword() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "그래프 탐색");
        setField(request, "tags", List.of("구현"));

        assertThat(analyzer.isTagOnlySearch(request)).isFalse();
        assertThat(analyzer.resolveKeywordText(request)).isEqualTo("그래프 탐색");
        assertThat(analyzer.resolveCanonicalTags(request)).containsExactly("구현");
    }

    @Test
    @DisplayName("플랫폼명과 보조 단어가 섞인 keyword는 제거하고 핵심 태그만 추출한다")
    void stripsPlatformAndNoiseWordsBeforeTagInference() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "프로그래머스 구현 문제");

        assertThat(analyzer.isTagOnlySearch(request)).isTrue();
        assertThat(analyzer.resolveKeywordText(request)).isBlank();
        assertThat(analyzer.resolveCanonicalTags(request)).containsExactly("구현");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
