package com.ctps.ctps_api.domain.problem.service.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultProblemSearchScorerTest {

    private final DefaultProblemSearchScorer scorer = new DefaultProblemSearchScorer(new NoopProblemPersonalizationScorer());
    private final DefaultSearchQueryPreprocessor preprocessor = new DefaultSearchQueryPreprocessor();

    @Test
    @DisplayName("제목 전체 일치, 태그 일치, 난이도 일치 시 높은 relevance score를 계산한다")
    void score_calculatesBreakdownForStrongMatch() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "실버 그래프");
        setField(request, "tags", List.of("그래프", "BFS"));
        setField(request, "difficulty", List.of(Problem.Difficulty.medium));
        setField(request, "platform", List.of("백준"));

        ExternalProblemSearchItemResponse item = ExternalProblemSearchItemResponse.builder()
                .id("solvedac-2178")
                .title("실버 그래프 미로 탐색")
                .platform("백준")
                .problemNumber("2178")
                .difficulty(Problem.Difficulty.medium)
                .tags(List.of("그래프", "BFS"))
                .externalUrl("https://www.acmicpc.net/problem/2178")
                .recommendationReason("그래프 탐색 대표 문제")
                .solved(false)
                .build();

        ProblemSearchScore score = scorer.score(
                preprocessor.process(request),
                item,
                ProviderScoreSignal.builder().normalizedScore(0.5).rawScore(5.0).build()
        );

        assertThat(score.getKeywordScore()).isEqualTo(10);
        assertThat(score.getTagScore()).isEqualTo(6);
        assertThat(score.getDifficultyScore()).isEqualTo(4);
        assertThat(score.getPlatformScore()).isEqualTo(2);
        assertThat(score.getUnsolvedBonus()).isEqualTo(0);
        assertThat(score.getProviderWeightedScore()).isEqualTo(2);
        assertThat(score.getTotalScore()).isEqualTo(24);
    }

    @Test
    @DisplayName("제목 일부 토큰만 맞고 난이도가 1단계 차이면 중간 점수를 준다")
    void score_returnsPartialMatchScore() throws Exception {
        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "DP 골드");
        setField(request, "difficulty", List.of(Problem.Difficulty.hard));

        ExternalProblemSearchItemResponse item = ExternalProblemSearchItemResponse.builder()
                .id("leetcode-64")
                .title("Minimum Path Sum DP")
                .platform("리트코드")
                .problemNumber("64")
                .difficulty(Problem.Difficulty.medium)
                .tags(List.of("DP"))
                .recommendationReason("동적 계획법 기본기 문제")
                .solved(false)
                .build();

        ProblemSearchScore score = scorer.score(
                preprocessor.process(request),
                item,
                ProviderScoreSignal.builder().normalizedScore(0.0).rawScore(0.0).build()
        );

        assertThat(score.getKeywordScore()).isEqualTo(7);
        assertThat(score.getDifficultyScore()).isEqualTo(2);
        assertThat(score.getTotalScore()).isEqualTo(9);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
