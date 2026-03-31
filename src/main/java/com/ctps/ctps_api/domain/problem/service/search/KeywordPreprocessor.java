package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.search.preprocess.SearchQueryNormalizerPipeline;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KeywordPreprocessor {

    private final SearchQueryNormalizerPipeline normalizerPipeline;

    public KeywordPreprocessor(SearchQueryNormalizerPipeline normalizerPipeline) {
        this.normalizerPipeline = normalizerPipeline;
    }

    public KeywordExpansion preprocess(String keyword) {
        String normalizedKeyword = normalize(keyword);
        List<String> keywordTokens = tokenize(normalizedKeyword);
        List<String> expandedKeywords = expand(normalizedKeyword, keywordTokens);
        return KeywordExpansion.builder()
                .normalizedKeyword(normalizedKeyword)
                .keywordTokens(keywordTokens)
                .expandedKeywords(expandedKeywords)
                .build();
    }

    private List<String> tokenize(String normalizedKeyword) {
        if (!StringUtils.hasText(normalizedKeyword)) {
            return List.of();
        }
        return Arrays.stream(normalizedKeyword.split("\\s+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> expand(String normalizedKeyword, List<String> keywordTokens) {
        if (!StringUtils.hasText(normalizedKeyword)) {
            return List.of();
        }

        Set<String> expanded = new LinkedHashSet<>();
        for (String token : keywordTokens) {
            if (token.length() >= 2) {
                expanded.add(token);
            }
            if (token.length() >= 3) {
                String prefix = token.substring(0, token.length() - 1).trim();
                if (prefix.length() >= 2 && !prefix.equals(token)) {
                    expanded.add(prefix);
                }
            }
        }

        if (keywordTokens.size() <= 1 && normalizedKeyword.length() >= 3) {
            for (int end = normalizedKeyword.length() - 1; end >= 2; end--) {
                String prefix = normalizedKeyword.substring(0, end).trim();
                if (!prefix.equals(normalizedKeyword) && prefix.length() >= 2) {
                    expanded.add(prefix);
                    break;
                }
            }
        }

        List<String> values = new ArrayList<>(expanded);
        values.remove(normalizedKeyword);
        return List.copyOf(values);
    }

    private String normalize(String text) {
        return StringUtils.hasText(text) ? normalizerPipeline.normalize(text) : "";
    }
}
