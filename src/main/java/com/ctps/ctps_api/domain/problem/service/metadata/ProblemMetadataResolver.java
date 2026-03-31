package com.ctps.ctps_api.domain.problem.service.metadata;

import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResponse;

public interface ProblemMetadataResolver {

    String platform();

    ProblemMetadataResponse resolve(String number, String link);
}
