package com.ctps.ctps_api.domain.problem.service.external;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemProvider;
import com.ctps.ctps_api.global.config.ExternalProviderRestClientFactory;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class LeetCodeExternalProblemProvider implements ExternalProblemProvider {

    private static final String BASE_URL = "https://leetcode.com";
    private final RestClient restClient;

    public LeetCodeExternalProblemProvider(ExternalProviderRestClientFactory restClientFactory) {
        this.restClient = restClientFactory.create(BASE_URL);
    }

    @Override
    public List<ExternalProblemSearchItemResponse> search(ProblemSearchRequest request) {
        if (!StringUtils.hasText(request.getKeyword()) && request.getDifficulty().isEmpty()) {
            return List.of();
        }

        try {
            LeetCodeProblemSetResponse response = restClient.get()
                    .uri("/api/problems/all/")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(LeetCodeProblemSetResponse.class);

            if (response == null || response.getStatStatusPairs() == null) {
                return List.of();
            }

            String keyword = request.getKeyword() == null ? "" : request.getKeyword().toLowerCase(Locale.ROOT);

            return response.getStatStatusPairs().stream()
                    .filter(item -> !item.isPaidOnly())
                    .filter(item -> keyword.isBlank() || item.getStat().getQuestionTitle().toLowerCase(Locale.ROOT).contains(keyword))
                    .filter(item -> request.getDifficulty().isEmpty()
                            || request.getDifficulty().contains(mapDifficulty(item.getDifficulty().getLevel())))
                    .limit(12)
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

    private Problem.Difficulty mapDifficulty(int level) {
        return switch (level) {
            case 1 -> Problem.Difficulty.easy;
            case 2 -> Problem.Difficulty.medium;
            default -> Problem.Difficulty.hard;
        };
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
}
