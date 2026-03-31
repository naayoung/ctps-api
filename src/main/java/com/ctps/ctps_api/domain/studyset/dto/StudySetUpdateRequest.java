package com.ctps.ctps_api.domain.studyset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;

@Getter
public class StudySetUpdateRequest {

    @Size(max = 100, message = "공부 세트 이름은 100자 이하로 입력해 주세요.")
    private String name;
    @Size(max = 200, message = "공부 세트 문제는 최대 200개까지 선택할 수 있습니다.")
    private List<@NotBlank(message = "문제 ID는 비워둘 수 없습니다.") String> problemIds;
    @Size(max = 200, message = "완료한 문제는 최대 200개까지 저장할 수 있습니다.")
    private List<@NotBlank(message = "완료한 문제 ID는 비워둘 수 없습니다.") String> completedProblemIds;
}
