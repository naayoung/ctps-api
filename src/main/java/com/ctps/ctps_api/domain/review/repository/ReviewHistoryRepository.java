package com.ctps.ctps_api.domain.review.repository;

import com.ctps.ctps_api.domain.review.entity.ReviewHistoryEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewHistoryRepository extends JpaRepository<ReviewHistoryEntry, Long> {

    List<ReviewHistoryEntry> findAllByProblemIdAndUserIdOrderByReviewedAtDesc(Long problemId, Long userId);

    List<ReviewHistoryEntry> findAllByUserIdAndReviewedAtBetween(Long userId, LocalDate startDate, LocalDate endDate);

    long countByUserIdAndReviewedAtBetween(Long userId, LocalDate startDate, LocalDate endDate);

    void deleteAllByProblemId(Long problemId);
}
