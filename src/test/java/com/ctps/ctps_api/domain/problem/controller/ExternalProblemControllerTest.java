package com.ctps.ctps_api.domain.problem.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.ProblemMetadataService;
import com.ctps.ctps_api.domain.problem.service.ProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.ProblemService;
import com.ctps.ctps_api.global.security.AdminAuthenticationInterceptor;
import com.ctps.ctps_api.global.security.AuthenticatedUser;
import com.ctps.ctps_api.global.security.ClientRequestResolver;
import com.ctps.ctps_api.global.security.CorsOriginProperties;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import com.ctps.ctps_api.global.security.CsrfProtectionInterceptor;
import com.ctps.ctps_api.global.security.InMemoryRateLimitService;
import com.ctps.ctps_api.global.security.UserAuthenticationInterceptor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ProblemController.class, ExternalProblemController.class})
class ExternalProblemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProblemService problemService;

    @MockBean
    private ProblemSearchService problemSearchService;

    @MockBean
    private ExternalProblemSearchService externalProblemSearchService;

    @MockBean
    private ProblemMetadataService problemMetadataService;

    @MockBean
    private UserAuthenticationInterceptor userAuthenticationInterceptor;

    @MockBean
    private AdminAuthenticationInterceptor adminAuthenticationInterceptor;

    @MockBean
    private CsrfProtectionInterceptor csrfProtectionInterceptor;

    @MockBean
    private InMemoryRateLimitService inMemoryRateLimitService;

    @MockBean
    private ClientRequestResolver clientRequestResolver;

    @MockBean
    private CorsOriginProperties corsOriginProperties;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            CurrentUserContext.set(AuthenticatedUser.builder()
                    .id(1L)
                    .username("tester")
                    .displayName("테스터")
                    .build());
            return true;
        }).when(userAuthenticationInterceptor).preHandle(any(), any(), any());
        doReturn(true).when(csrfProtectionInterceptor)
                .preHandle(any(), any(), any());
        given(clientRequestResolver.resolveClientKey(any())).willReturn("127.0.0.1");
    }

    @Test
    @DisplayName("외부 문제 검색 API는 추천 문제 목록과 페이지 메타데이터를 반환한다")
    void searchExternalProblems_returnsPagedResponse() throws Exception {
        ExternalProblemSearchResponse response = ExternalProblemSearchResponse.builder()
                .content(List.of(
                        ExternalProblemSearchItemResponse.builder()
                                .id("external-boj-2178")
                                .title("미로 탐색")
                                .platform("백준")
                                .problemNumber("2178")
                                .difficulty(Problem.Difficulty.medium)
                                .tags(List.of("그래프", "BFS"))
                                .externalUrl("https://www.acmicpc.net/problem/2178")
                                .recommendationReason("실버 그래프 검색과 자연스럽게 이어지는 대표 문제")
                                .solved(false)
                                .build()
                ))
                .page(0)
                .size(6)
                .totalElements(1)
                .totalPages(1)
                .hasNext(false)
                .build();

        given(externalProblemSearchService.search(any())).willReturn(response);

        mockMvc.perform(get("/api/external-problems/search")
                        .param("keyword", "실버 그래프")
                        .param("page", "0")
                        .param("size", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value("external-boj-2178"))
                .andExpect(jsonPath("$.data.content[0].externalUrl").value("https://www.acmicpc.net/problem/2178"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }
}
