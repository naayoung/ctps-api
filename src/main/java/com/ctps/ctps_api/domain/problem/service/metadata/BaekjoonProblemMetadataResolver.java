package com.ctps.ctps_api.domain.problem.service.metadata;

import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResponse;
import com.ctps.ctps_api.global.config.ExternalProviderRestClientFactory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class BaekjoonProblemMetadataResolver implements ProblemMetadataResolver {

    private final ExternalProviderRestClientFactory restClientFactory;

    public BaekjoonProblemMetadataResolver(ExternalProviderRestClientFactory restClientFactory) {
        this.restClientFactory = restClientFactory;
    }

    @Override
    public String platform() {
        return ProblemMetadataSupport.BAEKJOON_PLATFORM;
    }

    @Override
    public ProblemMetadataResponse resolve(String number, String link) {
        RestClient restClient = restClientFactory.create(ProblemMetadataSupport.SOLVED_AC_BASE_URL);
        SolvedAcProblemResponse response = restClient.get()
                .uri(URI.create(ProblemMetadataSupport.SOLVED_AC_BASE_URL + "/api/v3/problem/show?problemId="
                        + URLEncoder.encode(number, StandardCharsets.UTF_8)))
                .header("User-Agent", "ctps-problem-metadata/1.0")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(SolvedAcProblemResponse.class);

        if (response == null) {
            return ProblemMetadataSupport.notFound(platform(), number, link);
        }

        return ProblemMetadataResponse.builder()
                .platform(platform())
                .number(number)
                .link(ProblemMetadataSupport.buildCanonicalLink(platform(), number, link))
                .title(StringUtils.hasText(response.getTitleKo()) ? response.getTitleKo() : null)
                .tags(response.getTags() == null ? List.of() : response.getTags().stream()
                        .map(SolvedAcTag::preferredName)
                        .filter(StringUtils::hasText)
                        .toList())
                .difficulty(ProblemMetadataSupport.mapBaekjoonDifficulty(response.getLevel()))
                .metadataFound(true)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class SolvedAcProblemResponse {
        private String titleKo;
        private Integer level;
        private List<SolvedAcTag> tags;

        public String getTitleKo() {
            return titleKo;
        }

        public void setTitleKo(String titleKo) {
            this.titleKo = titleKo;
        }

        public Integer getLevel() {
            return level;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class SolvedAcTag {
        private List<SolvedAcTagDisplayName> displayNames;

        public List<SolvedAcTagDisplayName> getDisplayNames() {
            return displayNames;
        }

        public void setDisplayNames(List<SolvedAcTagDisplayName> displayNames) {
            this.displayNames = displayNames;
        }

        public String preferredName() {
            if (displayNames == null || displayNames.isEmpty()) {
                return null;
            }
            return displayNames.stream()
                    .filter(item -> "ko".equalsIgnoreCase(item.getLanguage()))
                    .map(SolvedAcTagDisplayName::getName)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElseGet(() -> displayNames.stream()
                            .map(SolvedAcTagDisplayName::getName)
                            .filter(StringUtils::hasText)
                            .findFirst()
                            .orElse(null));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class SolvedAcTagDisplayName {
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
