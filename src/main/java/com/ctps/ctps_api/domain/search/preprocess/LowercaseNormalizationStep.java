package com.ctps.ctps_api.domain.search.preprocess;

import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class LowercaseNormalizationStep implements SearchNormalizationStep {

    @Override
    public String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT);
    }
}
