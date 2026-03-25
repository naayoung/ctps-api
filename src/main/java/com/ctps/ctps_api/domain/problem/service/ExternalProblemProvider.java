package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import java.util.List;

public interface ExternalProblemProvider {

    List<ExternalProblemSearchItemResponse> search(ProblemSearchRequest request);

    default String providerKey() {
        return getClass().getSimpleName();
    }

    default String providerLabel() {
        return providerKey();
    }
}
