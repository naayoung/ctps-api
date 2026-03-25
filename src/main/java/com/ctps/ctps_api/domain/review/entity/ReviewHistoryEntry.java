package com.ctps.ctps_api.domain.review.entity;

import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "review_history_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int reviewCountAfterCheck;

    @Column(nullable = false)
    private int intervalDays;

    @Column(nullable = false)
    private LocalDate reviewedAt;

    @Column(nullable = false)
    private LocalDate nextReviewDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public ReviewHistoryEntry(
            Review review,
            Problem problem,
            User user,
            int reviewCountAfterCheck,
            int intervalDays,
            LocalDate reviewedAt,
            LocalDate nextReviewDate,
            LocalDateTime createdAt
    ) {
        this.review = review;
        this.problem = problem;
        this.user = user;
        this.reviewCountAfterCheck = reviewCountAfterCheck;
        this.intervalDays = intervalDays;
        this.reviewedAt = reviewedAt;
        this.nextReviewDate = nextReviewDate;
        this.createdAt = createdAt;
    }
}
