package com.ctps.ctps_api.domain.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "programmers_problem_catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgrammersProblemCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String externalId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 100)
    private String problemNumber;

    @Column(nullable = false, length = 20)
    private String difficulty;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String tagsJson;

    @Column(nullable = false, length = 1000)
    private String externalUrl;

    @Column(nullable = false, length = 1000)
    private String recommendationReason;

    @Column(nullable = false)
    private LocalDateTime sourceUpdatedAt;

    @Column(nullable = false)
    private LocalDateTime ingestedAt;

    @Builder
    public ProgrammersProblemCatalog(
            String externalId,
            String title,
            String problemNumber,
            String difficulty,
            String tagsJson,
            String externalUrl,
            String recommendationReason,
            LocalDateTime sourceUpdatedAt,
            LocalDateTime ingestedAt
    ) {
        this.externalId = externalId;
        this.title = title;
        this.problemNumber = problemNumber;
        this.difficulty = difficulty;
        this.tagsJson = tagsJson;
        this.externalUrl = externalUrl;
        this.recommendationReason = recommendationReason;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.ingestedAt = ingestedAt;
    }

    public void refresh(
            String title,
            String problemNumber,
            String difficulty,
            String tagsJson,
            String externalUrl,
            String recommendationReason,
            LocalDateTime sourceUpdatedAt,
            LocalDateTime ingestedAt
    ) {
        this.title = title;
        this.problemNumber = problemNumber;
        this.difficulty = difficulty;
        this.tagsJson = tagsJson;
        this.externalUrl = externalUrl;
        this.recommendationReason = recommendationReason;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.ingestedAt = ingestedAt;
    }
}
