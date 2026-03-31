package com.ctps.ctps_api.domain.review.entity;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false, unique = true)
    private Problem problem;

    @Column(nullable = false)
    private int reviewCount;

    @Column(nullable = false)
    private LocalDate lastReviewedDate;

    @Column(nullable = false)
    private LocalDate nextReviewDate;

    @Builder
    public Review(Problem problem, int reviewCount, LocalDate lastReviewedDate, LocalDate nextReviewDate) {
        this.problem = problem;
        this.reviewCount = reviewCount;
        this.lastReviewedDate = lastReviewedDate;
        this.nextReviewDate = nextReviewDate;
    }

    public void completeReview(LocalDate reviewedDate, LocalDate nextReviewDate) {
        this.reviewCount += 1;
        this.lastReviewedDate = reviewedDate;
        this.nextReviewDate = nextReviewDate;
    }

    public void markPending(LocalDate nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }

    public void resetCycle(LocalDate solvedDate) {
        this.reviewCount = 0;
        this.lastReviewedDate = solvedDate;
        this.nextReviewDate = solvedDate;
    }
}
