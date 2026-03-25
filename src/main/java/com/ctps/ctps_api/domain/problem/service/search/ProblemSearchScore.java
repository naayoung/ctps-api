package com.ctps.ctps_api.domain.problem.service.search;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProblemSearchScore {

    private int totalScore;
    private int ruleScore;
    private int keywordScore;
    private int tagScore;
    private int difficultyScore;
    private int unsolvedBonus;
    private int platformScore;
    private int personalizationScore;
    private int providerWeightedScore;
    private Double providerRawScore;
    private Double providerNormalizedScore;
}
