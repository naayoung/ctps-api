package com.ctps.ctps_api.domain.search.dto;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UnifiedSearchItemResponse {

    private String id;
    private SearchItemSource source;
    private String sourceLabel;
    private String providerKey;
    private String providerLabel;
    private String title;
    private String platform;
    private String problemNumber;
    private Problem.Difficulty difficulty;
    private String difficultyLabel;
    private java.util.List<String> tags;
    private String summary;
    private String description;
    private String externalUrl;
    private Problem.Result result;
    private boolean needsReview;
    private boolean bookmarked;
    private boolean solved;
    private LocalDate lastSolvedAt;
    private LocalDateTime createdAt;
    private Integer totalScore;
    private Integer ruleScore;
    private Double providerScore;
    private Double providerNormalizedScore;
}
