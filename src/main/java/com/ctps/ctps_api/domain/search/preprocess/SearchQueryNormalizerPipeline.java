package com.ctps.ctps_api.domain.search.preprocess;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SearchQueryNormalizerPipeline {

    private final List<SearchNormalizationStep> steps;

    public String normalize(String input) {
        String normalized = input == null ? "" : input;
        for (SearchNormalizationStep step : steps) {
            normalized = step.normalize(normalized);
        }
        return normalized;
    }
}
