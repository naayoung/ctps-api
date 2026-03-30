package com.ctps.ctps_api.domain.problem.service.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.search.SearchIntentAnalyzer;
import com.ctps.ctps_api.global.config.ExternalProviderRestClientFactory;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SolvedAcExternalProblemProviderTest {

    @Test
    @DisplayName("백준 난이도-only 검색은 solved.ac tier 쿼리로 fallback 한다")
    void search_usesTierQueryForDifficultyOnlyRequest() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://solved.ac");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(queryParam("query", "tier:s1..s5"))
                .andRespond(withSuccess("""
                        {
                          "count": 1,
                          "items": [
                            {
                              "problemId": 1002,
                              "titleKo": "터렛",
                              "level": 8,
                              "tags": []
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        SolvedAcExternalProblemProvider provider = new SolvedAcExternalProblemProvider(
                new TestExternalProviderRestClientFactory(builder),
                new SearchIntentAnalyzer()
        );

        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "platform", List.of("백준"));
        setField(request, "difficulty", List.of(Problem.Difficulty.medium));

        var result = provider.search(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("터렛");
        assertThat(result.get(0).getPlatform()).isEqualTo("백준");
        server.verify();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TestExternalProviderRestClientFactory extends ExternalProviderRestClientFactory {

        private final RestClient.Builder builder;

        private TestExternalProviderRestClientFactory(RestClient.Builder builder) {
            super(2000, 5000);
            this.builder = builder;
        }

        @Override
        public RestClient create(String baseUrl) {
            return builder.build();
        }
    }
}
