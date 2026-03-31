package com.ctps.ctps_api.domain.search.service;

import com.ctps.ctps_api.domain.search.dto.UnifiedSearchItemResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SearchResultDeduplicator {

    public List<UnifiedSearchItemResponse> deduplicate(List<UnifiedSearchItemResponse> items) {
        Map<String, UnifiedSearchItemResponse> deduplicated = new LinkedHashMap<>();
        for (UnifiedSearchItemResponse item : items) {
            deduplicated.putIfAbsent(keyOf(item), item);
        }
        return List.copyOf(deduplicated.values());
    }

    public int countDistinct(List<UnifiedSearchItemResponse> items) {
        return deduplicate(items).size();
    }

    public String keyOf(UnifiedSearchItemResponse item) {
        if (StringUtils.hasText(item.getPlatform()) && StringUtils.hasText(item.getProblemNumber())) {
            return item.getPlatform().trim().toLowerCase(java.util.Locale.ROOT)
                    + "|"
                    + item.getProblemNumber().trim().toLowerCase(java.util.Locale.ROOT);
        }
        return item.getSource().name() + "|" + item.getId();
    }
}
