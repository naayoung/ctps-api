package com.ctps.ctps_api.domain.problem.dto;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Size;

@Getter
@Setter
public class ProblemMetadataResolveRequest {

    @Size(max = 1000, message = "문제 링크는 1000자 이하로 입력해 주세요.")
    private String link;
    @Size(max = 50, message = "플랫폼은 50자 이하로 입력해 주세요.")
    private String platform;
    @Size(max = 100, message = "문제 번호는 100자 이하로 입력해 주세요.")
    private String number;
}
