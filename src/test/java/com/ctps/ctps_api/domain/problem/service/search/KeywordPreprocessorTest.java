package com.ctps.ctps_api.domain.problem.service.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctps.ctps_api.domain.search.preprocess.LowercaseNormalizationStep;
import com.ctps.ctps_api.domain.search.preprocess.SearchQueryNormalizerPipeline;
import com.ctps.ctps_api.domain.search.preprocess.SymbolNormalizationStep;
import com.ctps.ctps_api.domain.search.preprocess.TrimNormalizationStep;
import com.ctps.ctps_api.domain.search.preprocess.WhitespaceNormalizationStep;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeywordPreprocessorTest {

    private final KeywordPreprocessor keywordPreprocessor = new KeywordPreprocessor(
            new SearchQueryNormalizerPipeline(List.of(
                    new TrimNormalizationStep(),
                    new SymbolNormalizationStep(),
                    new LowercaseNormalizationStep(),
                    new WhitespaceNormalizationStep()
            ))
    );

    @Test
    @DisplayName("포도주 검색어는 포도까지 확장하지만 의미 없는 한 글자 확장은 제외한다")
    void preprocess_expandsMeaningfulPrefixOnly() {
        KeywordExpansion expansion = keywordPreprocessor.preprocess("포도주");

        assertThat(expansion.getExpandedKeywords()).contains("포도");
        assertThat(expansion.getExpandedKeywords()).doesNotContain("주");
        assertThat(expansion.getExpandedKeywords()).allMatch(keyword -> keyword.length() >= 2);
    }
}
