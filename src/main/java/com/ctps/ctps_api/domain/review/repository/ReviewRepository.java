package com.ctps.ctps_api.domain.review.repository;

import com.ctps.ctps_api.domain.review.entity.Review;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByProblemId(Long problemId);

    List<Review> findAllByNextReviewDateLessThanEqual(LocalDate date);

    Optional<Review> findByProblemIdAndProblemUserId(Long problemId, Long userId);

    List<Review> findAllByProblemUserIdAndNextReviewDateLessThanEqual(Long userId, LocalDate date);

    List<Review> findAllByProblemUserId(Long userId);
}
