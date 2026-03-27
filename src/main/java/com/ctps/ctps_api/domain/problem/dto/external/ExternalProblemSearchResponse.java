package com.ctps.ctps_api.domain.problem.dto.external;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class ExternalProblemSearchResponse {

    private List<ExternalProblemSearchItemResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private List<String> failedProviders;
    private String warningMessage;

    public static ExternalProblemSearchResponse from(Page<ExternalProblemSearchItemResponse> page) {
        return ExternalProblemSearchResponse.builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .failedProviders(List.of())
                .build();
    }
}
