package com.ctps.ctps_api.domain.search.entity;

import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.search.dto.SearchItemSource;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "problem_interaction_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemInteractionEvent {

    public enum EventType {
        SEARCH_CLICK,
        DETAIL_VIEW,
        BOOKMARK,
        MARK_REVIEW
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String problemRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SearchItemSource source;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(nullable = false, length = 100)
    private String problemNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Problem.Difficulty difficulty;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "problem_interaction_event_tags", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "tag", nullable = false, length = 100)
    private List<String> tags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType eventType;

    @Column(length = 255)
    private String sourceQuery;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public ProblemInteractionEvent(
            User user,
            String problemRef,
            SearchItemSource source,
            String platform,
            String problemNumber,
            Problem.Difficulty difficulty,
            List<String> tags,
            EventType eventType,
            String sourceQuery,
            LocalDateTime createdAt
    ) {
        this.user = user;
        this.problemRef = problemRef;
        this.source = source;
        this.platform = platform;
        this.problemNumber = problemNumber;
        this.difficulty = difficulty;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        this.eventType = eventType;
        this.sourceQuery = sourceQuery;
        this.createdAt = createdAt;
    }
}
