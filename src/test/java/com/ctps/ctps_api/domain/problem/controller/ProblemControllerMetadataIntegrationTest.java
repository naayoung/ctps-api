package com.ctps.ctps_api.domain.problem.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctps.ctps_api.domain.problem.repository.ProgrammersProblemCatalogRepository;
import com.ctps.ctps_api.domain.problem.service.ProblemMetadataService;
import com.ctps.ctps_api.domain.problem.service.ProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.ProblemService;
import com.ctps.ctps_api.global.config.ExternalProviderRestClientFactory;
import com.ctps.ctps_api.global.security.AdminAuthenticationInterceptor;
import com.ctps.ctps_api.global.security.CorsOriginProperties;
import com.ctps.ctps_api.global.security.CsrfProtectionInterceptor;
import com.ctps.ctps_api.global.security.UserAuthenticationInterceptor;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

@WebMvcTest(ProblemController.class)
@Import({
        ProblemMetadataService.class,
        ProblemControllerMetadataIntegrationTest.TestConfig.class
})
class ProblemControllerMetadataIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @MockBean
    private ProblemService problemService;

    @MockBean
    private ProblemSearchService problemSearchService;

    @MockBean
    private ProgrammersProblemCatalogRepository programmersProblemCatalogRepository;

    @MockBean
    private UserAuthenticationInterceptor userAuthenticationInterceptor;

    @MockBean
    private AdminAuthenticationInterceptor adminAuthenticationInterceptor;

    @MockBean
    private CsrfProtectionInterceptor csrfProtectionInterceptor;

    @MockBean
    private CorsOriginProperties corsOriginProperties;

    @BeforeEach
    void setUp() throws Exception {
        doReturn(true).when(userAuthenticationInterceptor)
                .preHandle(any(), any(), any());
        doReturn(true).when(csrfProtectionInterceptor)
                .preHandle(any(), any(), any());
    }

    @Test
    @DisplayName("메타데이터 resolve API는 프로그래머스 서버 렌더링 페이지 fallback 결과를 그대로 반환한다")
    void resolveProblemMetadata_returnsProgrammersFallbackMetadata() throws Exception {
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
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

        given(programmersProblemCatalogRepository.findFirstByProblemNumber("43162")).willReturn(Optional.empty());
        given(programmersProblemCatalogRepository.findFirstByExternalUrl(
                "https://school.programmers.co.kr/learn/courses/30/lessons/43162"
        )).willReturn(Optional.empty());

        mockMvc.perform(get("/api/problems/metadata/resolve")
                        .param("platform", "프로그래머스")
                        .param("number", "43162")
                        .param("link", "https://school.programmers.co.kr/learn/courses/30/lessons/43162"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platform").value("프로그래머스"))
                .andExpect(jsonPath("$.data.number").value("43162"))
                .andExpect(jsonPath("$.data.title").value("네트워크"))
                .andExpect(jsonPath("$.data.difficulty").value("medium"))
                .andExpect(jsonPath("$.data.tags").isArray())
                .andExpect(jsonPath("$.data.tags[0]").exists())
                .andExpect(jsonPath("$.data.metadataFound").value(true));

        server.verify();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder().baseUrl("https://school.programmers.co.kr");
        }

        @Bean
        ExternalProviderRestClientFactory externalProviderRestClientFactory(RestClient.Builder restClientBuilder) {
            return new ExternalProviderRestClientFactory(2000, 5000) {
                @Override
                public RestClient create(String baseUrl) {
                    return restClientBuilder.build();
                }
            };
        }
    }
}
