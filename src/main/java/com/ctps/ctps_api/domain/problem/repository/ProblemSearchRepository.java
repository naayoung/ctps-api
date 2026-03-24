package com.ctps.ctps_api.domain.problem.repository;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import org.springframework.data.domain.Page;

public interface ProblemSearchRepository {

    Page<Problem> searchProblems(ProblemSearchRequest request);
}
