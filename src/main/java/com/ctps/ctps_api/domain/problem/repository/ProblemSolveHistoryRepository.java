package com.ctps.ctps_api.domain.problem.repository;

import com.ctps.ctps_api.domain.problem.entity.ProblemSolveHistoryEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemSolveHistoryRepository extends JpaRepository<ProblemSolveHistoryEntry, Long> {

    List<ProblemSolveHistoryEntry> findAllByProblemIdAndUserIdOrderBySolvedAtDesc(Long problemId, Long userId);

    void deleteAllByProblemId(Long problemId);
}
