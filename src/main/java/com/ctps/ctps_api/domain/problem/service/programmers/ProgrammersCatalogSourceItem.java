package com.ctps.ctps_api.domain.problem.service.programmers;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor
public class ProgrammersCatalogSourceItem {

    private String externalId;
    private String title;
    private String problemNumber;
    private String difficulty;
    private List<String> tags;
    private String externalUrl;
    private String recommendationReason;
}
