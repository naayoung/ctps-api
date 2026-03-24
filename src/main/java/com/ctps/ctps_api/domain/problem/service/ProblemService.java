package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.dto.ProblemCreateRequest;
import com.ctps.ctps_api.domain.problem.dto.ProblemResponse;
import com.ctps.ctps_api.domain.problem.dto.ProblemUpdateRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ProblemRepository;
import com.ctps.ctps_api.global.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemService {

    private final ProblemRepository problemRepository;

    @Transactional
    public ProblemResponse createProblem(ProblemCreateRequest request) {
        Problem problem = Problem.builder()
                .platform(request.getPlatform())
                .title(resolveTitle(request.getTitle(), request.getNumber()))
                .number(request.getNumber())
                .link(request.getLink())
                .tags(request.getTags())
                .difficulty(request.getDifficulty())
                .memo(request.getMemo())
                .result(request.getResult())
                .needsReview(request.isNeedsReview())
                .reviewedAt(request.getReviewedAt())
                .reviewHistory(request.getReviewHistory())
                .createdAt(LocalDateTime.now())
                .solvedDates(request.getSolvedDates())
                .lastSolvedAt(request.getLastSolvedAt())
                .bookmarked(request.isBookmarked())
                .build();

        return ProblemResponse.from(problemRepository.save(problem));
    }

    public List<ProblemResponse> getProblems() {
        return problemRepository.findAll().stream().map(ProblemResponse::from).toList();
    }

    public ProblemResponse getProblem(Long id) {
        return ProblemResponse.from(findById(id));
    }

    @Transactional
    public ProblemResponse updateProblem(Long id, ProblemUpdateRequest request) {
        Problem problem = findById(id);
        problem.patch(
                request.getPlatform(),
                request.getTitle(),
                request.getNumber(),
                request.getLink(),
                request.getTags(),
                request.getDifficulty(),
                request.getMemo(),
                request.getResult(),
                request.getNeedsReview(),
                request.getReviewedAt(),
                request.getReviewHistory(),
                request.getSolvedDates(),
                request.getLastSolvedAt(),
                request.getBookmarked()
        );
        return ProblemResponse.from(problem);
    }

    @Transactional
    public void deleteProblem(Long id) {
        Problem problem = findById(id);
        problemRepository.delete(problem);
    }

    private Problem findById(Long id) {
        return problemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("문제를 찾을 수 없습니다. id=" + id));
    }

    private String resolveTitle(String title, String number) {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return number;
    }
}
