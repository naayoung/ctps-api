package com.ctps.ctps_api.domain.review.dto;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ReviewCheckRequest {

    private Problem.Result result;

    @Size(max = 2000, message = "메모는 2000자 이하로 입력해 주세요.")
    private String memo;
}
