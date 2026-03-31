package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.search.preprocess.LowercaseNormalizationStep;
import com.ctps.ctps_api.domain.search.preprocess.SearchQueryNormalizerPipeline;
import com.ctps.ctps_api.domain.search.service.SearchTypeCanonicalizer;
import com.ctps.ctps_api.domain.search.preprocess.SymbolNormalizationStep;
import com.ctps.ctps_api.domain.search.preprocess.TrimNormalizationStep;
import com.ctps.ctps_api.domain.search.preprocess.WhitespaceNormalizationStep;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DefaultSearchQueryPreprocessor implements SearchQueryPreprocessor {

    private final SearchQueryNormalizerPipeline normalizerPipeline;
    private final SearchIntentAnalyzer searchIntentAnalyzer;
    private final KeywordPreprocessor keywordPreprocessor;

    public DefaultSearchQueryPreprocessor() {
        this(new SearchQueryNormalizerPipeline(List.of(
                new TrimNormalizationStep(),
                new SymbolNormalizationStep(),
                new LowercaseNormalizationStep(),
                new WhitespaceNormalizationStep()
        )), new SearchIntentAnalyzer(new SearchTypeCanonicalizer()));
    }

    public DefaultSearchQueryPreprocessor(
            SearchQueryNormalizerPipeline normalizerPipeline,
            SearchIntentAnalyzer searchIntentAnalyzer
    ) {
        this(normalizerPipeline, searchIntentAnalyzer, new KeywordPreprocessor(normalizerPipeline));
    }

    public DefaultSearchQueryPreprocessor(
            SearchQueryNormalizerPipeline normalizerPipeline,
            SearchIntentAnalyzer searchIntentAnalyzer,
            KeywordPreprocessor keywordPreprocessor
    ) {
        this.normalizerPipeline = normalizerPipeline;
        this.searchIntentAnalyzer = searchIntentAnalyzer;
        this.keywordPreprocessor = keywordPreprocessor;
    }

    @Override
    public ProcessedSearchQuery process(ProblemSearchRequest request) {
        String rawKeyword = request.getKeyword();
        KeywordExpansion keywordExpansion = keywordPreprocessor.preprocess(searchIntentAnalyzer.resolveKeywordText(request));

        return ProcessedSearchQuery.builder()
                .rawKeyword(rawKeyword)
                .normalizedKeyword(keywordExpansion.getNormalizedKeyword())
                .keywordTokens(keywordExpansion.getKeywordTokens())
                .expandedKeywords(keywordExpansion.getExpandedKeywords())
                .normalizedPlatforms(request.getPlatform().stream().map(this::normalize).filter(StringUtils::hasText).toList())
                .normalizedTags(searchIntentAnalyzer.resolveCanonicalTags(request).stream()
                        .map(this::normalize)
                        .filter(StringUtils::hasText)
                        .toList())
                .requestedDifficulties(request.getDifficulty())
                .build();
    }

    private String normalize(String text) {
        return StringUtils.hasText(text) ? normalizerPipeline.normalize(text) : "";
    }
}
