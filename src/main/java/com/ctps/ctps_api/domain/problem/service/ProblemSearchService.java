package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemSearchService {

    private final ProblemRepository problemRepository;

    public ProblemSearchResponse search(ProblemSearchRequest request) {
        Page<Problem> searchPage = problemRepository.searchProblems(request);
        Page<ProblemSearchItemResponse> responsePage = searchPage.map(ProblemSearchItemResponse::from);
        return ProblemSearchResponse.from(responsePage);
    }
}
