package com.ctps.ctps_api.domain.studyset.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "study_sets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudySet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "study_set_problem_ids", joinColumns = @JoinColumn(name = "study_set_id"))
    @Column(name = "problem_id", nullable = false)
    private List<String> problemIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "study_set_completed_problem_ids", joinColumns = @JoinColumn(name = "study_set_id"))
    @Column(name = "problem_id", nullable = false)
    private List<String> completedProblemIds = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public StudySet(String name, List<String> problemIds, List<String> completedProblemIds, LocalDateTime createdAt) {
        this.name = name;
        this.problemIds = problemIds == null ? new ArrayList<>() : new ArrayList<>(problemIds);
        this.completedProblemIds = completedProblemIds == null ? new ArrayList<>() : new ArrayList<>(completedProblemIds);
        this.createdAt = createdAt;
    }

    public void patch(String name, List<String> problemIds, List<String> completedProblemIds) {
        if (name != null) this.name = name;
        if (problemIds != null) this.problemIds = new ArrayList<>(problemIds);
        if (completedProblemIds != null) this.completedProblemIds = new ArrayList<>(completedProblemIds);
    }
}
