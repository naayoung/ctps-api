package com.ctps.ctps_api.domain.problem.repository;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long>, ProblemSearchRepository {

    @EntityGraph(attributePaths = "tags")
    List<Problem> findByIdIn(List<Long> ids);
}
