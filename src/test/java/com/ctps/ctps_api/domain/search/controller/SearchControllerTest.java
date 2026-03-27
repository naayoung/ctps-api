package com.ctps.ctps_api.domain.search.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctps.ctps_api.domain.search.dto.FrequentSearchTypeItemResponse;
import com.ctps.ctps_api.domain.search.dto.FrequentSearchTypesResponse;
import com.ctps.ctps_api.domain.search.entity.ProblemInteractionEvent;
import com.ctps.ctps_api.domain.search.service.FrequentSearchTypeService;
import com.ctps.ctps_api.domain.search.service.SearchActivityService;
import com.ctps.ctps_api.domain.search.service.UnifiedSearchService;
import com.ctps.ctps_api.global.security.AdminAuthenticationInterceptor;
import com.ctps.ctps_api.global.security.ClientRequestResolver;
import com.ctps.ctps_api.global.security.CorsOriginProperties;
import com.ctps.ctps_api.global.security.CsrfProtectionInterceptor;
import com.ctps.ctps_api.global.security.InMemoryRateLimitService;
import com.ctps.ctps_api.global.security.UserAuthenticationInterceptor;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UnifiedSearchService unifiedSearchService;

    @MockBean
    private SearchActivityService searchActivityService;

    @MockBean
    private FrequentSearchTypeService frequentSearchTypeService;

    @MockBean
    private InMemoryRateLimitService rateLimitService;

    @MockBean
    private ClientRequestResolver clientRequestResolver;

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
        doReturn(true).when(userAuthenticationInterceptor).preHandle(any(), any(), any());
        doReturn(true).when(csrfProtectionInterceptor).preHandle(any(), any(), any());
        given(clientRequestResolver.resolveClientKey(any())).willReturn("test-client");
    }

    @Test
    @DisplayName("자주 찾는 유형 API는 집계 결과를 그대로 반환한다")
    void getFrequentTypes_returnsResponse() throws Exception {
        given(frequentSearchTypeService.getFrequentTypes()).willReturn(FrequentSearchTypesResponse.builder()
                .generatedAt(LocalDateTime.of(2026, 3, 27, 12, 0))
                .periodDays(30)
                .hasEnoughData(true)
                .items(List.of(
                        FrequentSearchTypeItemResponse.builder()
                                .type(FrequentSearchTypeItemResponse.Type.TAG)
                                .key("그래프")
                                .label("그래프")
                                .score(12.0)
                                .build(),
                        FrequentSearchTypeItemResponse.builder()
                                .type(FrequentSearchTypeItemResponse.Type.DIFFICULTY)
                                .key("medium")
                                .label("보통")
                                .score(9.0)
                                .build()
                ))
                .build());

        mockMvc.perform(get("/api/search/frequent-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hasEnoughData").value(true))
                .andExpect(jsonPath("$.data.items[0].label").value("그래프"))
                .andExpect(jsonPath("$.data.items[1].type").value("DIFFICULTY"));
    }

    @Test
    @DisplayName("문제 상호작용 기록 API는 요청을 서비스에 전달한다")
    void recordProblemInteractionEvent_delegatesToService() throws Exception {
        mockMvc.perform(post("/api/search/events/problem-interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "problemRef": "101",
                                  "source": "INTERNAL",
                                  "platform": "백준",
                                  "problemNumber": "101",
                                  "difficulty": "medium",
                                  "tags": ["그래프", "BFS"],
                                  "eventType": "DETAIL_VIEW",
                                  "sourceQuery": "실버 그래프"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(searchActivityService).recordInteractionEvent(any());
    }
}
