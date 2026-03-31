package com.ctps.ctps_api.domain.problem.entity;

import com.ctps.ctps_api.domain.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "problem_solve_attempt_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemSolveHistoryEntry {

    public enum ActivityType {
        solve,
        review
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityType activityType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Problem.Result result;

    @Column(nullable = false, length = 2000)
    private String memo;

    @Column(nullable = false)
    private LocalDateTime solvedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public ProblemSolveHistoryEntry(
            Problem problem,
            User user,
            ActivityType activityType,
            Problem.Result result,
            String memo,
            LocalDateTime solvedAt,
            LocalDateTime createdAt
    ) {
        this.problem = problem;
        this.user = user;
        this.activityType = activityType == null ? ActivityType.solve : activityType;
        this.result = result;
        this.memo = memo;
        this.solvedAt = solvedAt;
        this.createdAt = createdAt;
    }
}
