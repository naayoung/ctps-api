package com.ctps.ctps_api.domain.search.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FrequentSearchTypesResponse {

    private LocalDateTime generatedAt;
    private int periodDays;
    private List<FrequentSearchTypeItemResponse> items;
    private List<FrequentSearchTypeItemResponse> focusedItems;
    private List<FrequentSearchTypeItemResponse> reviewNeededItems;
    private boolean hasEnoughData;
}
