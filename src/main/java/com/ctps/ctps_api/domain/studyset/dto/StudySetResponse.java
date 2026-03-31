package com.ctps.ctps_api.domain.studyset.dto;

import com.ctps.ctps_api.domain.studyset.entity.StudySet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudySetResponse {

    private String id;
    private String name;
    private List<String> problemIds;
    private List<String> completedProblemIds;
    private LocalDateTime createdAt;

    public static StudySetResponse from(StudySet studySet) {
        return StudySetResponse.builder()
                .id(String.valueOf(studySet.getId()))
                .name(studySet.getName())
                .problemIds(new ArrayList<>(studySet.getProblemIds()))
                .completedProblemIds(new ArrayList<>(studySet.getCompletedProblemIds()))
                .createdAt(studySet.getCreatedAt())
                .build();
    }
}
