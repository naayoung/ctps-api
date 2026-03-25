package com.ctps.ctps_api.domain.problem.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.ProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.ProblemService;
import com.ctps.ctps_api.global.security.AdminAuthenticationInterceptor;
import com.ctps.ctps_api.global.security.CorsOriginProperties;
import com.ctps.ctps_api.global.security.CsrfProtectionInterceptor;
import com.ctps.ctps_api.global.security.UserAuthenticationInterceptor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProblemController.class)
class ProblemControllerSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProblemService problemService;

    @MockBean
    private ProblemSearchService problemSearchService;

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
    @DisplayName("검색 API는 페이지 메타데이터와 결과 목록을 반환한다")
    void searchProblems_returnsPagedResponse() throws Exception {
        ProblemSearchResponse response = ProblemSearchResponse.builder()
                .content(List.of(
                        ProblemSearchItemResponse.builder()
                                .id("1")
                                .title("특정한 최단 경로")
                                .platform("백준")
                                .problemNumber("1504")
                                .difficulty(Problem.Difficulty.hard)
                                .tags(List.of("그래프", "다익스트라"))
                                .result(Problem.Result.fail)
                                .needsReview(true)
                                .lastSolvedAt(LocalDate.of(2026, 3, 19))
                                .createdAt(LocalDateTime.of(2026, 3, 10, 12, 0))
                                .build()
                ))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .hasNext(false)
                .build();

        given(problemSearchService.search(any())).willReturn(response);

        mockMvc.perform(get("/api/problems/search")
                        .param("keyword", "dfs")
                        .param("platform", "백준")
                        .param("difficulty", "medium")
                        .param("result", "fail")
                        .param("needsReview", "true")
                        .param("sort", "lastSolvedAt,desc")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value("1"))
                .andExpect(jsonPath("$.data.content[0].title").value("특정한 최단 경로"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }
}
