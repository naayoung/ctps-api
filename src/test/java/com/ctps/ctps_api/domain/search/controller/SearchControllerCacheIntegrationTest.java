package com.ctps.ctps_api.domain.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.entity.ExternalProblemCache;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ExternalProblemCacheRepository;
import com.ctps.ctps_api.domain.problem.service.external.LeetCodeExternalProblemProvider;
import com.ctps.ctps_api.domain.problem.service.external.ProgrammersCatalogExternalProblemProvider;
import com.ctps.ctps_api.domain.problem.service.external.SolvedAcExternalProblemProvider;
import com.ctps.ctps_api.global.security.CsrfProtectionInterceptor;
import com.ctps.ctps_api.global.security.UserAuthenticationInterceptor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:search-cache-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=NUMBER",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class SearchControllerCacheIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExternalProblemCacheRepository externalProblemCacheRepository;

    @MockBean
    private SolvedAcExternalProblemProvider solvedAcExternalProblemProvider;

    @MockBean
    private LeetCodeExternalProblemProvider leetCodeExternalProblemProvider;

    @MockBean
    private ProgrammersCatalogExternalProblemProvider programmersCatalogExternalProblemProvider;

    @MockBean
    private UserAuthenticationInterceptor userAuthenticationInterceptor;

    @MockBean
    private CsrfProtectionInterceptor csrfProtectionInterceptor;

    @BeforeEach
    void setUp() throws Exception {
        externalProblemCacheRepository.deleteAll();
        doReturn(true).when(userAuthenticationInterceptor).preHandle(any(), any(), any());
        doReturn(true).when(csrfProtectionInterceptor).preHandle(any(), any(), any());

        given(solvedAcExternalProblemProvider.providerKey()).willReturn("solvedac");
        given(solvedAcExternalProblemProvider.providerLabel()).willReturn("solved.ac");
        given(solvedAcExternalProblemProvider.search(any())).willReturn(List.of(
                ExternalProblemSearchItemResponse.builder()
                        .id("solvedac-2178")
                        .title("미로 탐색")
                        .platform("백준")
                        .problemNumber("2178")
                        .difficulty(Problem.Difficulty.medium)
                        .tags(List.of("그래프", "BFS"))
                        .externalUrl("https://www.acmicpc.net/problem/2178")
                        .recommendationReason("그래프 검색 결과")
                        .solved(false)
                        .build()
        ));

        given(leetCodeExternalProblemProvider.providerKey()).willReturn("leetcode");
        given(leetCodeExternalProblemProvider.providerLabel()).willReturn("LeetCode");
        given(leetCodeExternalProblemProvider.search(any())).willReturn(List.of());

        given(programmersCatalogExternalProblemProvider.providerKey()).willReturn("programmers");
        given(programmersCatalogExternalProblemProvider.providerLabel()).willReturn("프로그래머스");
        given(programmersCatalogExternalProblemProvider.search(any())).willReturn(List.of());
    }

    @Test
    @DisplayName("/api/search 호출 시 외부 검색 캐시가 별도 쓰기 트랜잭션으로 저장된다")
    void search_savesExternalProblemCache() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("keyword", "그래프")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.externalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("solvedac-2178"));

        List<ExternalProblemCache> caches = externalProblemCacheRepository.findAll();
        assertThat(caches).isNotEmpty();
        assertThat(caches)
                .extracting(ExternalProblemCache::getProvider)
                .contains("solvedac");
        assertThat(caches.stream()
                .filter(cache -> "solvedac".equals(cache.getProvider()))
                .map(ExternalProblemCache::getQueryKey)
                .toList())
                .anyMatch(queryKey -> queryKey.contains("|provider=solvedac|"));
    }
}
