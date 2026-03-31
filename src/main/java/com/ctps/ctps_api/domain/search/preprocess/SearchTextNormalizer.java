package com.ctps.ctps_api.domain.search.preprocess;

import java.util.Locale;
import org.springframework.util.StringUtils;

public final class SearchTextNormalizer {

    private SearchTextNormalizer() {
    }

    public static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
