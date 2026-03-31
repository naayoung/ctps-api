package com.ctps.ctps_api.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResolveRequest;
import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResponse;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ProgrammersProblemCatalogRepository;
import com.ctps.ctps_api.domain.problem.service.metadata.ProgrammersProblemMetadataResolver;
import com.ctps.ctps_api.global.config.ExternalProviderRestClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProblemMetadataServiceTest {

    @Test
    @DisplayName("프로그래머스 카탈로그에 없는 링크도 페이지 메타데이터로 제목과 태그를 보강한다")
    void resolve_enrichesProgrammersMetadataFromLessonPageWhenCatalogMisses() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://school.programmers.co.kr");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://school.programmers.co.kr/learn/courses/30/lessons/43165"))
                .andExpect(header("User-Agent", "ctps-problem-metadata/1.0"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <title>타겟 넘버 | 프로그래머스 스쿨</title>
                          </head>
                          <body>
                            <script id="__NEXT_DATA__" type="application/json">
                              {
                                "props": {
                                  "pageProps": {
                                    "lesson": {
                                      "lessonId": 43165,
                                      "title": "타겟 넘버",
                                      "difficulty": "2",
                                      "tags": [
                                        {"name": "깊이 우선 탐색(DFS)"},
                                        {"name": "너비 우선 탐색(BFS)"}
                                      ]
                                    }
                                  }
                                }
                              }
                            </script>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        ProgrammersProblemCatalogRepository repository = Mockito.mock(ProgrammersProblemCatalogRepository.class);
        given(repository.findFirstByProblemNumber("43165")).willReturn(Optional.empty());
        given(repository.findFirstByExternalUrl("https://school.programmers.co.kr/learn/courses/30/lessons/43165"))
                .willReturn(Optional.empty());

        ProblemMetadataService service = new ProblemMetadataService(
                List.of(new ProgrammersProblemMetadataResolver(
                        new TestExternalProviderRestClientFactory(builder),
                        repository,
                        new ObjectMapper()
                ))
        );

        ProblemMetadataResolveRequest request = new ProblemMetadataResolveRequest();
        request.setPlatform("프로그래머스");
        request.setNumber("43165");
        request.setLink("https://school.programmers.co.kr/learn/courses/30/lessons/43165");

        ProblemMetadataResponse response = service.resolve(request);

        assertThat(response.isMetadataFound()).isTrue();
        assertThat(response.getTitle()).isEqualTo("타겟 넘버");
        assertThat(response.getDifficulty()).isEqualTo(Problem.Difficulty.medium);
        assertThat(response.getTags()).contains("깊이 우선 탐색", "DFS", "너비 우선 탐색", "BFS");
        server.verify();
    }

    @Test
    @DisplayName("프로그래머스 서버 렌더링 페이지에서도 breadcrumb와 data attribute로 제목과 태그를 보강한다")
    void resolve_enrichesProgrammersMetadataFromServerRenderedLessonPage() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://school.programmers.co.kr");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://school.programmers.co.kr/learn/courses/30/lessons/43162"))
                .andExpect(header("User-Agent", "ctps-problem-metadata/1.0"))
                .andRespond(withSuccess("""
                        <!DOCTYPE html>
                        <html lang="ko">
                        <head>
                          <meta name="twitter:title" property="og:title" content="코딩테스트 연습 - 네트워크">
                          <title>코딩테스트 연습 - 네트워크 | 프로그래머스 스쿨</title>
                        </head>
                        <body>
                          <ol class="breadcrumb">
                            <li><a href="/learn/challenges">코딩테스트 연습</a></li>
                            <li><a href="/learn/courses/30/parts/12421">깊이/너비 우선 탐색(DFS/BFS)</a></li>
                            <li class="active">네트워크</li>
                          </ol>
                          <div class="lesson-content"
                               data-lesson-id="43162"
                               data-lesson-title="네트워크"
                               data-challenge-level="3">
                            <span class="challenge-title">네트워크</span>
                          </div>
                        </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        ProgrammersProblemCatalogRepository repository = Mockito.mock(ProgrammersProblemCatalogRepository.class);
        given(repository.findFirstByProblemNumber("43162")).willReturn(Optional.empty());
        given(repository.findFirstByExternalUrl("https://school.programmers.co.kr/learn/courses/30/lessons/43162"))
                .willReturn(Optional.empty());

        ProblemMetadataService service = new ProblemMetadataService(
                List.of(new ProgrammersProblemMetadataResolver(
                        new TestExternalProviderRestClientFactory(builder),
                        repository,
                        new ObjectMapper()
                ))
        );

        ProblemMetadataResolveRequest request = new ProblemMetadataResolveRequest();
        request.setPlatform("프로그래머스");
        request.setNumber("43162");
        request.setLink("https://school.programmers.co.kr/learn/courses/30/lessons/43162");

        ProblemMetadataResponse response = service.resolve(request);

        assertThat(response.isMetadataFound()).isTrue();
        assertThat(response.getTitle()).isEqualTo("네트워크");
        assertThat(response.getDifficulty()).isEqualTo(Problem.Difficulty.medium);
        assertThat(response.getTags()).contains("깊이", "너비 우선 탐색", "DFS", "BFS", "깊이/너비 우선 탐색(DFS/BFS)");
        server.verify();
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
