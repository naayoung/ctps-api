package com.ctps.ctps_api.domain.problem.service.external;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemProvider;
import com.ctps.ctps_api.domain.problem.service.search.SearchIntentAnalyzer;
import com.ctps.ctps_api.global.config.ExternalProviderRestClientFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class LeetCodeExternalProblemProvider implements ExternalProblemProvider {

    private static final String BASE_URL = "https://leetcode.com";
    private static final Map<String, List<String>> LEETCODE_TOPIC_SLUGS = new LinkedHashMap<>();

    static {
        LEETCODE_TOPIC_SLUGS.put("DFS", List.of("depth-first-search"));
        LEETCODE_TOPIC_SLUGS.put("BFS", List.of("breadth-first-search"));
        LEETCODE_TOPIC_SLUGS.put("DP", List.of("dynamic-programming"));
        LEETCODE_TOPIC_SLUGS.put("그리디", List.of("greedy"));
        LEETCODE_TOPIC_SLUGS.put("그래프", List.of("graph"));
        LEETCODE_TOPIC_SLUGS.put("이분탐색", List.of("binary-search"));
        LEETCODE_TOPIC_SLUGS.put("브루트포스", List.of("backtracking", "recursion"));
        LEETCODE_TOPIC_SLUGS.put("자료구조", List.of("hash-table", "stack", "queue", "heap-priority-queue"));
        LEETCODE_TOPIC_SLUGS.put("구현", List.of("simulation", "string", "array"));
    }

    private final RestClient restClient;
    private final SearchIntentAnalyzer searchIntentAnalyzer;

    public LeetCodeExternalProblemProvider(
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
        String keyword = searchIntentAnalyzer.resolveKeywordText(request);
        List<String> effectiveTags = searchIntentAnalyzer.resolveCanonicalTags(request);
        if (!StringUtils.hasText(keyword) && request.getDifficulty().isEmpty() && effectiveTags.isEmpty()) {
            return List.of();
        }

        try {
            if (!effectiveTags.isEmpty()) {
                List<ExternalProblemSearchItemResponse> tagMatches = searchByTopicTags(effectiveTags, request);
                if (!tagMatches.isEmpty()) {
                    return tagMatches;
                }
            }

            LeetCodeProblemSetResponse response = restClient.get()
                    .uri("/api/problems/all/")
                    .header("User-Agent", "ctps-external-search/1.0")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(LeetCodeProblemSetResponse.class);

            if (response == null || response.getStatStatusPairs() == null) {
                return List.of();
            }

            String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);

            return response.getStatStatusPairs().stream()
                    .filter(item -> !item.isPaidOnly())
                    .filter(item -> normalizedKeyword.isBlank()
                            || item.getStat().getQuestionTitle().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                    .filter(item -> request.getDifficulty().isEmpty()
                            || request.getDifficulty().contains(mapDifficulty(item.getDifficulty().getLevel())))
                    .limit(15)
                    .map(item -> ExternalProblemSearchItemResponse.builder()
                            .id("leetcode-" + item.getStat().getFrontendQuestionId())
                            .providerKey(providerKey())
                            .providerLabel(providerLabel())
                            .title(item.getStat().getQuestionTitle())
                            .platform("리트코드")
                            .problemNumber(String.valueOf(item.getStat().getFrontendQuestionId()))
                            .difficulty(mapDifficulty(item.getDifficulty().getLevel()))
                            .tags(List.of())
                            .externalUrl("https://leetcode.com/problems/" + item.getStat().getQuestionTitleSlug() + "/")
                            .summary("LeetCode 공개 문제셋에서 매칭한 외부 문제")
                            .recommendationReason("LeetCode 공개 문제셋에서 찾은 추천 문제")
                            .solved(false)
                            .build())
                    .toList();
        } catch (Exception exception) {
            log.warn("leetcode provider failed", exception);
            throw new IllegalStateException("leetcode provider failed", exception);
        }
    }

    @Override
    public String providerKey() {
        return "leetcode";
    }

    @Override
    public String providerLabel() {
        return "LeetCode";
    }

    private List<ExternalProblemSearchItemResponse> searchByTopicTags(
            List<String> effectiveTags,
            ProblemSearchRequest request
    ) {
        List<String> topicSlugs = effectiveTags.stream()
                .flatMap(tag -> LEETCODE_TOPIC_SLUGS.getOrDefault(tag, List.of()).stream())
                .distinct()
                .toList();
        if (topicSlugs.isEmpty()) {
            return List.of();
        }

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("tags", topicSlugs);
        String difficultyFilter = toDifficultyFilter(request.getDifficulty());
        if (StringUtils.hasText(difficultyFilter)) {
            filters.put("difficulty", difficultyFilter);
        }

        LeetCodeTaggedQuestionListResponse response = restClient.post()
                .uri("/graphql/")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "query", """
                                query problemsetQuestionList($categorySlug: String, $limit: Int, $skip: Int, $filters: QuestionListFilterInput) {
                                  problemsetQuestionList(categorySlug: $categorySlug, limit: $limit, skip: $skip, filters: $filters) {
                                    questions {
                                      frontendQuestionId
                                      title
                                      titleSlug
                                      difficulty
                                      isPaidOnly
                                      topicTags {
                                        name
                                        slug
                                      }
                                    }
                                  }
                                }
                                """,
                        "variables", Map.of(
                                "categorySlug", "",
                                "limit", 15,
                                "skip", 0,
                                "filters", filters
                        )
                ))
                .retrieve()
                .body(LeetCodeTaggedQuestionListResponse.class);

        if (response == null || response.getData() == null
                || response.getData().getProblemsetQuestionList() == null
                || response.getData().getProblemsetQuestionList().getQuestions() == null) {
            return List.of();
        }

        return response.getData().getProblemsetQuestionList().getQuestions().stream()
                .filter(question -> !question.isPaidOnly())
                .map(question -> ExternalProblemSearchItemResponse.builder()
                        .id("leetcode-" + question.getFrontendQuestionId())
                        .providerKey(providerKey())
                        .providerLabel(providerLabel())
                        .title(question.getTitle())
                        .platform("리트코드")
                        .problemNumber(String.valueOf(question.getFrontendQuestionId()))
                        .difficulty(mapDifficulty(question.getDifficulty()))
                        .tags(question.getTopicTags() == null ? List.of() : question.getTopicTags().stream()
                                .map(LeetCodeTopicTag::getName)
                                .filter(StringUtils::hasText)
                                .toList())
                        .externalUrl("https://leetcode.com/problems/" + question.getTitleSlug() + "/")
                        .summary("LeetCode 태그 기반으로 매칭한 외부 문제")
                        .recommendationReason("LeetCode 태그 매칭으로 찾은 추천 문제")
                        .solved(false)
                        .build())
                .toList();
    }

    private Problem.Difficulty mapDifficulty(int level) {
        return switch (level) {
            case 1 -> Problem.Difficulty.easy;
            case 2 -> Problem.Difficulty.medium;
            default -> Problem.Difficulty.hard;
        };
    }

    private Problem.Difficulty mapDifficulty(String difficulty) {
        String normalized = difficulty == null ? "" : difficulty.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "EASY" -> Problem.Difficulty.easy;
            case "MEDIUM" -> Problem.Difficulty.medium;
            default -> Problem.Difficulty.hard;
        };
    }

    private String toDifficultyFilter(List<Problem.Difficulty> difficulties) {
        if (difficulties == null || difficulties.size() != 1 || difficulties.get(0) == null) {
            return null;
        }
        return switch (difficulties.get(0)) {
            case easy -> "EASY";
            case medium -> "MEDIUM";
            case hard -> "HARD";
        };
    }

    private boolean supportsPlatform(ProblemSearchRequest request) {
        return request.getPlatform().isEmpty()
                || request.getPlatform().stream().anyMatch(platform -> "리트코드".equals(platform));
    }

    public static final class LeetCodeProblemSetResponse {
        private List<LeetCodeProblemItem> statStatusPairs;

        public List<LeetCodeProblemItem> getStatStatusPairs() {
            return statStatusPairs;
        }

        public void setStatStatusPairs(List<LeetCodeProblemItem> statStatusPairs) {
            this.statStatusPairs = statStatusPairs;
        }
    }

    public static final class LeetCodeProblemItem {
        private LeetCodeProblemStat stat;
        private LeetCodeDifficulty difficulty;
        private boolean paidOnly;

        public LeetCodeProblemStat getStat() {
            return stat;
        }

        public void setStat(LeetCodeProblemStat stat) {
            this.stat = stat;
        }

        public LeetCodeDifficulty getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(LeetCodeDifficulty difficulty) {
            this.difficulty = difficulty;
        }

        public boolean isPaidOnly() {
            return paidOnly;
        }

        public void setPaidOnly(boolean paidOnly) {
            this.paidOnly = paidOnly;
        }
    }

    public static final class LeetCodeProblemStat {
        private Integer frontendQuestionId;
        private String questionTitle;
        private String questionTitleSlug;

        public Integer getFrontendQuestionId() {
            return frontendQuestionId;
        }

        public void setFrontendQuestionId(Integer frontendQuestionId) {
            this.frontendQuestionId = frontendQuestionId;
        }

        public String getQuestionTitle() {
            return questionTitle;
        }

        public void setQuestionTitle(String questionTitle) {
            this.questionTitle = questionTitle;
        }

        public String getQuestionTitleSlug() {
            return questionTitleSlug;
        }

        public void setQuestionTitleSlug(String questionTitleSlug) {
            this.questionTitleSlug = questionTitleSlug;
        }
    }

    public static final class LeetCodeDifficulty {
        private Integer level;

        public Integer getLevel() {
            return level == null ? 0 : level;
        }

        public void setLevel(Integer level) {
            this.level = level;
        }
    }

    public static final class LeetCodeTaggedQuestionListResponse {
        private LeetCodeTaggedQuestionListData data;

        public LeetCodeTaggedQuestionListData getData() {
            return data;
        }

        public void setData(LeetCodeTaggedQuestionListData data) {
            this.data = data;
        }
    }

    public static final class LeetCodeTaggedQuestionListData {
        private LeetCodeProblemsetQuestionList problemsetQuestionList;

        public LeetCodeProblemsetQuestionList getProblemsetQuestionList() {
            return problemsetQuestionList;
        }

        public void setProblemsetQuestionList(LeetCodeProblemsetQuestionList problemsetQuestionList) {
            this.problemsetQuestionList = problemsetQuestionList;
        }
    }

    public static final class LeetCodeProblemsetQuestionList {
        private List<LeetCodeTaggedQuestion> questions;

        public List<LeetCodeTaggedQuestion> getQuestions() {
            return questions;
        }

        public void setQuestions(List<LeetCodeTaggedQuestion> questions) {
            this.questions = questions;
        }
    }

    public static final class LeetCodeTaggedQuestion {
        private Integer frontendQuestionId;
        private String title;
        private String titleSlug;
        private String difficulty;
        private boolean isPaidOnly;
        private List<LeetCodeTopicTag> topicTags;

        public Integer getFrontendQuestionId() {
            return frontendQuestionId;
        }

        public void setFrontendQuestionId(Integer frontendQuestionId) {
            this.frontendQuestionId = frontendQuestionId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTitleSlug() {
            return titleSlug;
        }

        public void setTitleSlug(String titleSlug) {
            this.titleSlug = titleSlug;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(String difficulty) {
            this.difficulty = difficulty;
        }

        public boolean isPaidOnly() {
            return isPaidOnly;
        }

        public void setPaidOnly(boolean paidOnly) {
            isPaidOnly = paidOnly;
        }

        public List<LeetCodeTopicTag> getTopicTags() {
            return topicTags;
        }

        public void setTopicTags(List<LeetCodeTopicTag> topicTags) {
            this.topicTags = topicTags;
        }
    }

    public static final class LeetCodeTopicTag {
        private String name;
        private String slug;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }
    }
}
