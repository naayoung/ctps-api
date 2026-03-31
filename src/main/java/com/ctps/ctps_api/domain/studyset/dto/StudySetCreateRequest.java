package com.ctps.ctps_api.domain.studyset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;

@Getter
public class StudySetCreateRequest {

    @NotBlank
    @Size(max = 100, message = "공부 세트 이름은 100자 이하로 입력해 주세요.")
    private String name;

    @NotNull
    @Size(min = 1, max = 200, message = "공부 세트 문제는 1개 이상 200개 이하로 선택해 주세요.")
    private List<@NotBlank(message = "문제 ID는 비워둘 수 없습니다.") String> problemIds;
}
