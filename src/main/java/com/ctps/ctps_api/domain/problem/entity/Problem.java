package com.ctps.ctps_api.domain.problem.entity;

import com.ctps.ctps_api.domain.auth.entity.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "problems")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Problem {

    public enum Difficulty {
        easy,
        medium,
        hard
    }

    public enum Result {
        success,
        fail,
        partial
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 100)
    private String number;

    @Column(nullable = false, length = 1000)
    private String link;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "problem_tags", joinColumns = @JoinColumn(name = "problem_id"))
    @Column(name = "tag", nullable = false, length = 100)
    private List<String> tags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @Column(nullable = false, length = 2000)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Result result;

    @Column(nullable = false)
    private boolean needsReview;

    private LocalDate reviewedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "problem_review_history", joinColumns = @JoinColumn(name = "problem_id"))
    @Column(name = "reviewed_date", nullable = false)
    private List<LocalDate> reviewHistory = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "problem_solved_dates", joinColumns = @JoinColumn(name = "problem_id"))
    @Column(name = "solved_date", nullable = false)
    private List<LocalDate> solvedDates = new ArrayList<>();

    private LocalDate lastSolvedAt;

    @Column(nullable = false)
    private boolean bookmarked;

    @Builder
    public Problem(
            String platform,
            User user,
            String title,
            String number,
            String link,
            List<String> tags,
            Difficulty difficulty,
            String memo,
            Result result,
            boolean needsReview,
            LocalDate reviewedAt,
            List<LocalDate> reviewHistory,
            LocalDateTime createdAt,
            List<LocalDate> solvedDates,
            LocalDate lastSolvedAt,
            boolean bookmarked
    ) {
        this.user = user;
        this.platform = platform;
        this.title = title;
        this.number = number;
        this.link = link;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        this.difficulty = difficulty;
        this.memo = memo;
        if (result != null) this.result = result;
        this.needsReview = needsReview;
        if (reviewedAt != null) this.reviewedAt = reviewedAt;
        this.reviewHistory = reviewHistory == null ? new ArrayList<>() : new ArrayList<>(reviewHistory);
        this.createdAt = createdAt;
        this.solvedDates = solvedDates == null ? new ArrayList<>() : new ArrayList<>(solvedDates);
        if (lastSolvedAt != null) this.lastSolvedAt = lastSolvedAt;
        this.bookmarked = bookmarked;
    }

    public void patch(
            String platform,
            String title,
            String number,
            String link,
            List<String> tags,
            Difficulty difficulty,
            String memo,
            Result result,
            Boolean needsReview,
            LocalDate reviewedAt,
            List<LocalDate> reviewHistory,
            List<LocalDate> solvedDates,
            LocalDate lastSolvedAt,
            Boolean bookmarked
    ) {
        if (platform != null) this.platform = platform;
        if (title != null) this.title = title;
        if (number != null) this.number = number;
        if (link != null) this.link = link;
        if (tags != null) this.tags = new ArrayList<>(tags);
        if (difficulty != null) this.difficulty = difficulty;
        if (memo != null) this.memo = memo;
        this.result = result;
        if (needsReview != null) this.needsReview = needsReview;
        this.reviewedAt = reviewedAt;
        if (reviewHistory != null) this.reviewHistory = new ArrayList<>(reviewHistory);
        if (solvedDates != null) this.solvedDates = new ArrayList<>(solvedDates);
        this.lastSolvedAt = lastSolvedAt;
        if (bookmarked != null) this.bookmarked = bookmarked;
    }

    public void markReviewRequired() {
        this.needsReview = true;
    }

    public void markReviewCompleted(LocalDate reviewedDate) {
        this.needsReview = false;
        this.reviewedAt = reviewedDate;
        if (!this.reviewHistory.contains(reviewedDate)) {
            this.reviewHistory.add(reviewedDate);
        }
    }
}
