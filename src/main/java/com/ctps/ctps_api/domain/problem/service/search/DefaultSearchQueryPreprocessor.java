package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DefaultSearchQueryPreprocessor implements SearchQueryPreprocessor {

    @Override
    public ProcessedSearchQuery process(ProblemSearchRequest request) {
        String rawKeyword = request.getKeyword();
        String normalizedKeyword = normalize(rawKeyword);

        return ProcessedSearchQuery.builder()
                .rawKeyword(rawKeyword)
                .normalizedKeyword(normalizedKeyword)
                .keywordTokens(tokenize(normalizedKeyword))
                .normalizedPlatforms(request.getPlatform().stream().map(this::normalize).filter(StringUtils::hasText).toList())
                .normalizedTags(request.getTags().stream().map(this::normalize).filter(StringUtils::hasText).toList())
                .requestedDifficulties(request.getDifficulty())
                .build();
    }

    private List<String> tokenize(String normalizedKeyword) {
        if (!StringUtils.hasText(normalizedKeyword)) {
            return List.of();
        }
        return Arrays.stream(normalizedKeyword.split("\\s+"))
                .filter(StringUtils::hasText)
                .toList();
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
