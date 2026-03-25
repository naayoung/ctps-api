package com.ctps.ctps_api.domain.search.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UnifiedSearchResponse {

    private String query;
    private List<String> normalizedTokens;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private int internalCount;
    private int externalCount;
    private List<UnifiedSearchItemResponse> items;
}
