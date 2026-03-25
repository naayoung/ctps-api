package com.ctps.ctps_api.domain.problem.service.search;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;

public interface SearchQueryPreprocessor {

    ProcessedSearchQuery process(ProblemSearchRequest request);
}
