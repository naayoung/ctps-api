package com.ctps.ctps_api.domain.problem.dto;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;

@Getter
public class ProblemCreateRequest {

    @NotBlank
    @Size(max = 50, message = "플랫폼은 50자 이하로 입력해 주세요.")
    private String platform;

    @Size(max = 255, message = "문제 제목은 255자 이하로 입력해 주세요.")
    private String title;

    @NotBlank
    @Size(max = 100, message = "문제 번호는 100자 이하로 입력해 주세요.")
    private String number;

    @Size(max = 1000, message = "문제 링크는 1000자 이하로 입력해 주세요.")
    private String link;

    @NotNull
    @Size(max = 20, message = "태그는 최대 20개까지 입력할 수 있습니다.")
    private List<@NotBlank(message = "태그는 비워둘 수 없습니다.") @Size(max = 100, message = "태그는 100자 이하로 입력해 주세요.") String> tags;

    @NotNull
    private Problem.Difficulty difficulty;

    @NotNull
    @Size(max = 2000, message = "메모는 2000자 이하로 입력해 주세요.")
    private String memo;

    private Problem.Result result;

    private boolean needsReview;

    private LocalDate reviewedAt;

    @NotNull
    @Size(max = 365, message = "복습 이력은 최대 365개까지 저장할 수 있습니다.")
    private List<LocalDate> reviewHistory;

    @NotNull
    @Size(max = 365, message = "풀이 이력은 최대 365개까지 저장할 수 있습니다.")
    private List<LocalDate> solvedDates;

    private LocalDate lastSolvedAt;

    private boolean bookmarked;
}
