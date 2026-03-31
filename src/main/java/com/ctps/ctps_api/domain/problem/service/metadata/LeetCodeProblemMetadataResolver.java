package com.ctps.ctps_api.domain.problem.service.metadata;

import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResponse;
import com.ctps.ctps_api.domain.problem.service.external.LeetCodeExternalProblemProvider.LeetCodeProblemItem;
import com.ctps.ctps_api.domain.problem.service.external.LeetCodeExternalProblemProvider.LeetCodeProblemSetResponse;
import com.ctps.ctps_api.global.config.ExternalProviderRestClientFactory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Comparator;
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
public class LeetCodeProblemMetadataResolver implements ProblemMetadataResolver {

    private final ExternalProviderRestClientFactory restClientFactory;

    public LeetCodeProblemMetadataResolver(ExternalProviderRestClientFactory restClientFactory) {
        this.restClientFactory = restClientFactory;
    }

    @Override
    public String platform() {
        return ProblemMetadataSupport.LEETCODE_PLATFORM;
    }

    @Override
    public ProblemMetadataResponse resolve(String numberOrSlug, String link) {
        RestClient restClient = restClientFactory.create(ProblemMetadataSupport.LEETCODE_BASE_URL);
        LeetCodeProblemSetResponse response = restClient.get()
                .uri("/api/problems/all/")
                .header("User-Agent", "ctps-problem-metadata/1.0")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(LeetCodeProblemSetResponse.class);

        if (response == null || response.getStatStatusPairs() == null) {
            return ProblemMetadataSupport.notFound(
                    platform(),
                    numberOrSlug,
                    ProblemMetadataSupport.buildCanonicalLink(platform(), numberOrSlug, link)
            );
        }

        String normalized = numberOrSlug.trim().toLowerCase(Locale.ROOT);
        LeetCodeProblemItem item = response.getStatStatusPairs().stream()
                .filter(candidate -> candidate.getStat() != null)
                .filter(candidate -> matchesProblem(candidate, normalized))
                .min(Comparator.comparingInt(candidate ->
                        candidate.getStat().getFrontendQuestionId() == null
                                ? Integer.MAX_VALUE
                                : candidate.getStat().getFrontendQuestionId()))
                .orElse(null);

        if (item == null || item.getStat() == null) {
            return ProblemMetadataSupport.notFound(
                    platform(),
                    numberOrSlug,
                    ProblemMetadataSupport.buildCanonicalLink(platform(), numberOrSlug, link)
            );
        }

        String slug = item.getStat().getQuestionTitleSlug();
        return ProblemMetadataResponse.builder()
                .platform(platform())
                .number(StringUtils.hasText(numberOrSlug) && numberOrSlug.chars().allMatch(Character::isDigit)
                        ? numberOrSlug
                        : slug)
                .link(ProblemMetadataSupport.buildCanonicalLink(platform(), slug, link))
                .title(item.getStat().getQuestionTitle())
                .tags(fetchTags(restClient, slug))
                .difficulty(ProblemMetadataSupport.mapLeetCodeDifficulty(item.getDifficulty()))
                .metadataFound(true)
                .build();
    }

    private boolean matchesProblem(LeetCodeProblemItem item, String normalized) {
        String frontendId = item.getStat().getFrontendQuestionId() == null
                ? ""
                : String.valueOf(item.getStat().getFrontendQuestionId());
        String slug = item.getStat().getQuestionTitleSlug() == null
                ? ""
                : item.getStat().getQuestionTitleSlug().toLowerCase(Locale.ROOT);
        return normalized.equals(frontendId) || normalized.equals(slug);
    }

    private List<String> fetchTags(RestClient restClient, String slug) {
        if (!StringUtils.hasText(slug)) {
            return List.of();
        }

        try {
            LeetCodeQuestionResponse response = restClient.post()
                    .uri("/graphql/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "query", "query questionTags($titleSlug: String!) { question(titleSlug: $titleSlug) { topicTags { name } } }",
                            "variables", Map.of("titleSlug", slug)
                    ))
                    .retrieve()
                    .body(LeetCodeQuestionResponse.class);

            if (response == null || response.getData() == null || response.getData().getQuestion() == null
                    || response.getData().getQuestion().getTopicTags() == null) {
                return List.of();
            }

            return response.getData().getQuestion().getTopicTags().stream()
                    .map(LeetCodeTopicTag::getName)
                    .filter(StringUtils::hasText)
                    .toList();
        } catch (Exception exception) {
            log.debug("leetcode tag metadata resolve skipped slug={}", slug, exception);
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class LeetCodeQuestionResponse {
        private LeetCodeQuestionData data;

        public LeetCodeQuestionData getData() {
            return data;
        }

        public void setData(LeetCodeQuestionData data) {
            this.data = data;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class LeetCodeQuestionData {
        private LeetCodeQuestion question;

        public LeetCodeQuestion getQuestion() {
            return question;
        }

        public void setQuestion(LeetCodeQuestion question) {
            this.question = question;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class LeetCodeQuestion {
        private List<LeetCodeTopicTag> topicTags;

        public List<LeetCodeTopicTag> getTopicTags() {
            return topicTags;
        }

        public void setTopicTags(List<LeetCodeTopicTag> topicTags) {
            this.topicTags = topicTags;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class LeetCodeTopicTag {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
