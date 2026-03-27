package com.ctps.ctps_api.domain.search.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FrequentSearchTypeItemResponse {

    public enum Type {
        TAG,
        DIFFICULTY
    }

    private Type type;
    private String key;
    private String label;
    private double score;
    private int evidenceCount;
    private LocalDateTime lastActivityAt;
}
