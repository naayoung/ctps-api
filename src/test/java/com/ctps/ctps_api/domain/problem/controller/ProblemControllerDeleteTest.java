package com.ctps.ctps_api.domain.problem.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctps.ctps_api.domain.problem.service.ProblemMetadataService;
import com.ctps.ctps_api.domain.problem.service.ProblemSearchService;
import com.ctps.ctps_api.domain.problem.service.ProblemService;
import com.ctps.ctps_api.global.security.AdminAuthenticationInterceptor;
import com.ctps.ctps_api.global.security.CorsOriginProperties;
import com.ctps.ctps_api.global.security.CsrfProtectionInterceptor;
import com.ctps.ctps_api.global.security.UserAuthenticationInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProblemController.class)
class ProblemControllerDeleteTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProblemService problemService;

    @MockBean
    private ProblemSearchService problemSearchService;

    @MockBean
    private ProblemMetadataService problemMetadataService;

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
    @DisplayName("문제 삭제 API는 삭제 성공 응답을 반환한다")
    void deleteProblem_returnsSuccess() throws Exception {
        mockMvc.perform(delete("/api/problems/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("문제 삭제 성공"));

        then(problemService).should().deleteProblem(1L);
    }
}
