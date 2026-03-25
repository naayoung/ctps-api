package com.ctps.ctps_api.domain.search.preprocess;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class TrimNormalizationStep implements SearchNormalizationStep {

    @Override
    public String normalize(String input) {
        return input == null ? "" : input.trim();
    }
}
