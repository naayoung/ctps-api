package com.ctps.ctps_api.domain.studyset.dto;

import java.util.List;
import lombok.Getter;

@Getter
public class StudySetUpdateRequest {

    private String name;
    private List<String> problemIds;
    private List<String> completedProblemIds;
}
