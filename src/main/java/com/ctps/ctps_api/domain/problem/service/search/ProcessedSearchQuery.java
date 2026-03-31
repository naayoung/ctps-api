package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProcessedSearchQuery {

    private String rawKeyword;
    private String normalizedKeyword;
    private List<String> keywordTokens;
    private List<String> expandedKeywords;
    private List<String> normalizedPlatforms;
    private List<String> normalizedTags;
    private List<Problem.Difficulty> requestedDifficulties;
}
