package com.ctps.ctps_api.domain.auth.entity;

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
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(unique = true, length = 255)
    private String email;

    @Column(length = 1000)
    private String profileImageUrl;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public User(
            String username,
            String passwordHash,
            String displayName,
            String email,
            String profileImageUrl,
            LocalDateTime createdAt
    ) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.createdAt = createdAt;
    }

    public void updateProfile(String displayName, String email, String profileImageUrl) {
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName;
        }
        if (email != null && !email.isBlank()) {
            this.email = email;
        }
        if (profileImageUrl != null && !profileImageUrl.isBlank()) {
            this.profileImageUrl = profileImageUrl;
        }
    }
}
