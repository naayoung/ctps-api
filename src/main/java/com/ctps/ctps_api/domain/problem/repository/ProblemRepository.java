package com.ctps.ctps_api.domain.problem.repository;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
}
