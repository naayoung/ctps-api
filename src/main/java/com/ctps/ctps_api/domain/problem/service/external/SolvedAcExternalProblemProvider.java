package com.ctps.ctps_api.domain.problem.service.external;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemProvider;
import com.ctps.ctps_api.domain.problem.service.search.SearchIntentAnalyzer;
import com.ctps.ctps_api.global.config.ExternalProviderRestClientFactory;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class SolvedAcExternalProblemProvider implements ExternalProblemProvider {

    private static final String BASE_URL = "https://solved.ac";
    private static final Map<String, String> SOLVED_AC_TAG_IDS = new LinkedHashMap<>();

    static {
        SOLVED_AC_TAG_IDS.put("그래프", "graphs");
        SOLVED_AC_TAG_IDS.put("DP", "dp");
        SOLVED_AC_TAG_IDS.put("브루트포스", "bruteforcing");
        SOLVED_AC_TAG_IDS.put("구현", "implementation");
        SOLVED_AC_TAG_IDS.put("BFS", "bfs");
        SOLVED_AC_TAG_IDS.put("DFS", "dfs");
        SOLVED_AC_TAG_IDS.put("그리디", "greedy");
        SOLVED_AC_TAG_IDS.put("이분탐색", "binary_search");
        SOLVED_AC_TAG_IDS.put("자료구조", "data_structures");
    }

    private final RestClient restClient;
    private final SearchIntentAnalyzer searchIntentAnalyzer;

    public SolvedAcExternalProblemProvider(
            ExternalProviderRestClientFactory restClientFactory,
            SearchIntentAnalyzer searchIntentAnalyzer
    ) {
        this.restClient = restClientFactory.create(BASE_URL);
        this.searchIntentAnalyzer = searchIntentAnalyzer;
    }

    @Override
    public List<ExternalProblemSearchItemResponse> search(ProblemSearchRequest request) {
        if (!supportsPlatform(request)) {
            return List.of();
        }
        String textKeyword = searchIntentAnalyzer.resolveKeywordText(request);
        List<String> effectiveTags = searchIntentAnalyzer.resolveCanonicalTags(request);
        String difficultyToken = toDifficultyQueryToken(request.getDifficulty());
        if (!StringUtils.hasText(textKeyword) && effectiveTags.isEmpty() && !StringUtils.hasText(difficultyToken)) {
            return List.of();
        }

        try {
            String query = buildQuery(textKeyword, effectiveTags, difficultyToken);
            log.info(
                    "solved.ac query platform={} keyword={} tags={} difficulties={} builtQuery={}",
                    request.getPlatform(),
                    textKeyword,
                    effectiveTags,
                    request.getDifficulty(),
                    query
            );
            SolvedAcSearchResponse response = restClient.get()
                    .uri(buildSearchUri(query))
                    .header("User-Agent", "ctps-external-search/1.0")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(SolvedAcSearchResponse.class);

            if (response == null || response.getItems() == null) {
                return List.of();
            }

            return response.getItems().stream()
                    .limit(15)
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
                                    .map(names -> names.stream()
                                            .map(SolvedAcTagDisplayName::getName)
                                            .filter(StringUtils::hasText)
                                            .findFirst()
                                            .orElse(null))
                                    .filter(StringUtils::hasText)
                                    .toList())
                            .externalUrl("https://www.acmicpc.net/problem/" + item.getProblemId())
                            .summary(null)
                            .recommendationReason(null)
                            .solved(false)
                            .build())
                    .toList();
        } catch (RestClientResponseException exception) {
            log.warn(
                    "solved.ac provider failed status={} responseBody={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );
            throw new IllegalStateException("solved.ac provider failed", exception);
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

    private String buildQuery(String textKeyword, List<String> effectiveTags, String difficultyToken) {
        List<String> queryTerms = new java.util.ArrayList<>();
        if (StringUtils.hasText(textKeyword)) {
            queryTerms.add(textKeyword.trim());
        }
        if (!effectiveTags.isEmpty()) {
            queryTerms.addAll(effectiveTags.stream()
                    .map(this::toSolvedAcTagToken)
                    .filter(StringUtils::hasText)
                    .toList());
        }
        if (StringUtils.hasText(difficultyToken)) {
            queryTerms.add(difficultyToken);
        }
        return String.join(" ", queryTerms).trim();
    }

    private boolean supportsPlatform(ProblemSearchRequest request) {
        return request.getPlatform().isEmpty() || request.getPlatform().stream().anyMatch(platform -> "백준".equals(platform));
    }

    private String toSolvedAcTagToken(String tag) {
        String normalized = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }

        return SOLVED_AC_TAG_IDS.entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).equals(normalized))
                .map(entry -> "#" + entry.getValue())
                .findFirst()
                .orElse(tag.trim());
    }

    private String toDifficultyQueryToken(List<Problem.Difficulty> difficulties) {
        if (difficulties == null || difficulties.size() != 1 || difficulties.get(0) == null) {
            return "";
        }

        return switch (difficulties.get(0)) {
            case easy -> "tier:b1..b5";
            case medium -> "tier:s1..s5";
            case hard -> "tier:g1..r5";
        };
    }

    private URI buildSearchUri(String query) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return URI.create(BASE_URL + "/api/v3/search/problem?query=" + encodedQuery + "&page=1");
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
        private List<SolvedAcTagDisplayName> displayNames;

        public List<SolvedAcTagDisplayName> getDisplayNames() {
            return displayNames;
        }

        public void setDisplayNames(List<SolvedAcTagDisplayName> displayNames) {
            this.displayNames = displayNames;
        }
    }

    public static final class SolvedAcTagDisplayName {
        private String language;
        private String name;

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
