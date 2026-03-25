package com.ctps.ctps_api.domain.problem.service.external;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemProvider;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class SolvedAcExternalProblemProvider implements ExternalProblemProvider {

    private static final String BASE_URL = "https://solved.ac";
    private final RestClient restClient = RestClient.builder()
            .baseUrl(BASE_URL)
            .build();

    @Override
    public List<ExternalProblemSearchItemResponse> search(ProblemSearchRequest request) {
        if (!StringUtils.hasText(request.getKeyword()) && request.getTags().isEmpty()) {
            return List.of();
        }

        try {
            String query = buildQuery(request);
            SolvedAcSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v3/search/problem")
                            .queryParam("query", query)
                            .queryParam("page", 1)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(SolvedAcSearchResponse.class);

            if (response == null || response.getItems() == null) {
                return List.of();
            }

            return response.getItems().stream()
                    .limit(12)
                    .map(item -> ExternalProblemSearchItemResponse.builder()
                            .id("solvedac-" + item.getProblemId())
                            .providerKey(providerKey())
                            .providerLabel(providerLabel())
                            .title(item.getTitleKo())
                            .platform("백준")
                            .problemNumber(String.valueOf(item.getProblemId()))
                            .difficulty(mapDifficulty(item.getLevel()))
                            .tags(item.getTags() == null ? List.of() : item.getTags().stream()
                                    .map(SolvedAcTag::getDisplayNames)
                                    .filter(names -> names != null && !names.isEmpty())
                                    .map(names -> names.get(0))
                                    .toList())
                            .externalUrl("https://www.acmicpc.net/problem/" + item.getProblemId())
                            .summary("solved.ac 검색 결과를 기반으로 매핑한 백준 문제")
                            .recommendationReason("solved.ac 기반으로 찾은 백준 추천 문제")
                            .solved(false)
                            .build())
                    .toList();
        } catch (Exception exception) {
            log.warn("solved.ac provider failed", exception);
            throw new IllegalStateException("solved.ac provider failed", exception);
        }
    }

    @Override
    public String providerKey() {
        return "solvedac";
    }

    @Override
    public String providerLabel() {
        return "solved.ac";
    }

    private String buildQuery(ProblemSearchRequest request) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(request.getKeyword())) {
            builder.append(request.getKeyword().trim());
        }
        if (!request.getTags().isEmpty()) {
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(String.join(" ", request.getTags()));
        }
        return builder.toString().trim();
    }

    private Problem.Difficulty mapDifficulty(int level) {
        if (level >= 11) {
            return Problem.Difficulty.hard;
        }
        if (level >= 6) {
            return Problem.Difficulty.medium;
        }
        return Problem.Difficulty.easy;
    }

    public static final class SolvedAcSearchResponse {
        private List<SolvedAcProblemItem> items;

        public List<SolvedAcProblemItem> getItems() {
            return items;
        }

        public void setItems(List<SolvedAcProblemItem> items) {
            this.items = items;
        }
    }

    public static final class SolvedAcProblemItem {
        private Integer problemId;
        private String titleKo;
        private Integer level;
        private List<SolvedAcTag> tags;

        public Integer getProblemId() {
            return problemId;
        }

        public void setProblemId(Integer problemId) {
            this.problemId = problemId;
        }

        public String getTitleKo() {
            return titleKo;
        }

        public void setTitleKo(String titleKo) {
            this.titleKo = titleKo;
        }

        public Integer getLevel() {
            return level == null ? 0 : level;
        }

        public void setLevel(Integer level) {
            this.level = level;
        }

        public List<SolvedAcTag> getTags() {
            return tags;
        }

        public void setTags(List<SolvedAcTag> tags) {
            this.tags = tags;
        }
    }

    public static final class SolvedAcTag {
        private List<String> displayNames;

        public List<String> getDisplayNames() {
            return displayNames;
        }

        public void setDisplayNames(List<String> displayNames) {
            this.displayNames = displayNames;
        }
    }
}
