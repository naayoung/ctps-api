package com.ctps.ctps_api.domain.problem.service.search;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KeywordExpansion {

    private String normalizedKeyword;
    private List<String> keywordTokens;
    private List<String> expandedKeywords;
}
