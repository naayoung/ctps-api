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
@Table(name = "external_problem_cache")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalProblemCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false, length = 500)
    private String queryKey;

    @Column(nullable = false, length = 500)
    private String queryHash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contentJson;

    @Column(nullable = false)
    private long totalElements;

    @Column(nullable = false)
    private int totalPages;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public ExternalProblemCache(
            String provider,
            String queryKey,
            String queryHash,
            String contentJson,
            long totalElements,
            int totalPages,
            LocalDateTime fetchedAt,
            LocalDateTime expiresAt
    ) {
        this.provider = provider;
        this.queryKey = queryKey;
        this.queryHash = queryHash;
        this.contentJson = contentJson;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.fetchedAt = fetchedAt;
        this.expiresAt = expiresAt;
    }

    public void refresh(
            String contentJson,
            long totalElements,
            int totalPages,
            LocalDateTime fetchedAt,
            LocalDateTime expiresAt
    ) {
        this.contentJson = contentJson;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.fetchedAt = fetchedAt;
        this.expiresAt = expiresAt;
    }
}
