package com.ctps.ctps_api.domain.problem.service.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.service.search.SearchIntentAnalyzer;
import com.ctps.ctps_api.global.config.ExternalProviderRestClientFactory;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LeetCodeExternalProblemProviderTest {

    @Test
    @DisplayName("리트코드 태그-only 검색은 GraphQL 태그 필터로 fallback 한다")
    void search_usesTopicTagQueryForTagOnlyRequest() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://leetcode.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://leetcode.com/graphql/"))
                .andExpect(header("Content-Type", "application/json"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "problemsetQuestionList": {
                              "questions": [
                                {
                                  "frontendQuestionId": 200,
                                  "title": "Number of Islands",
                                  "titleSlug": "number-of-islands",
                                  "difficulty": "MEDIUM",
                                  "isPaidOnly": false,
                                  "topicTags": [
                                    {
                                      "name": "Depth-First Search",
                                      "slug": "depth-first-search"
                                    }
                                  ]
                                }
                              ]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        LeetCodeExternalProblemProvider provider = new LeetCodeExternalProblemProvider(
                new TestExternalProviderRestClientFactory(builder),
                new SearchIntentAnalyzer()
        );

        ProblemSearchRequest request = new ProblemSearchRequest();
        setField(request, "keyword", "DFS");

        var result = provider.search(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlatform()).isEqualTo("리트코드");
        assertThat(result.get(0).getTitle()).isEqualTo("Number of Islands");
        assertThat(result.get(0).getTags()).contains("Depth-First Search");
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
