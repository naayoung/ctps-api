package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.search.preprocess.LowercaseNormalizationStep;
import com.ctps.ctps_api.domain.search.preprocess.SearchQueryNormalizerPipeline;
import com.ctps.ctps_api.domain.search.preprocess.SymbolNormalizationStep;
import com.ctps.ctps_api.domain.search.preprocess.TrimNormalizationStep;
import com.ctps.ctps_api.domain.search.preprocess.WhitespaceNormalizationStep;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class DefaultSearchQueryPreprocessor implements SearchQueryPreprocessor {

    private final SearchQueryNormalizerPipeline normalizerPipeline;

    public DefaultSearchQueryPreprocessor() {
        this(new SearchQueryNormalizerPipeline(List.of(
                new TrimNormalizationStep(),
                new SymbolNormalizationStep(),
                new LowercaseNormalizationStep(),
                new WhitespaceNormalizationStep()
        )));
    }

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
        return StringUtils.hasText(text) ? normalizerPipeline.normalize(text) : "";
    }
}
