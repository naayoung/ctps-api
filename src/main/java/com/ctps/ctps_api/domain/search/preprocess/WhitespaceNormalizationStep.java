package com.ctps.ctps_api.domain.search.preprocess;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class WhitespaceNormalizationStep implements SearchNormalizationStep {

    @Override
    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return input.replaceAll("\\s+", " ").trim();
    }
}
