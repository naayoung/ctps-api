package com.ctps.ctps_api.domain.search.repository;

import com.ctps.ctps_api.domain.search.entity.ProblemInteractionEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemInteractionEventRepository extends JpaRepository<ProblemInteractionEvent, Long> {

    @EntityGraph(attributePaths = "tags")
    List<ProblemInteractionEvent> findAllByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime createdAt
    );

    @EntityGraph(attributePaths = "tags")
    List<ProblemInteractionEvent> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
