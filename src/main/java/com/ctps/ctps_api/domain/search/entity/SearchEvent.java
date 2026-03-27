package com.ctps.ctps_api.domain.search.entity;

import com.ctps.ctps_api.domain.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "search_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String query;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public SearchEvent(User user, String query, LocalDateTime createdAt) {
        this.user = user;
        this.query = query;
        this.createdAt = createdAt;
    }
}
