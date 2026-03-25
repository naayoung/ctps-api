package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ProblemRepository;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemSearchService {

    private final ProblemRepository problemRepository;

    public ProblemSearchResponse search(ProblemSearchRequest request) {
        Long userId = CurrentUserContext.getOptional()
                .map(user -> user.getId())
                .orElse(null);
        if (userId == null) {
            return ProblemSearchResponse.from(new PageImpl<ProblemSearchItemResponse>(java.util.List.of(), request.toPageable(), 0));
        }
        Page<Problem> searchPage = problemRepository.searchProblems(userId, request);
        Page<ProblemSearchItemResponse> responsePage = searchPage.map(ProblemSearchItemResponse::from);
        return ProblemSearchResponse.from(responsePage);
    }
}
