package com.ctps.ctps_api.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider primaryAuthProvider;

    @Column(length = 255)
    private String primaryProviderUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime emailVerifiedAt;

    private LocalDateTime deletedAt;

    @Builder
    public User(
            String username,
            String passwordHash,
            String displayName,
            String email,
            String profileImageUrl,
            AuthProvider primaryAuthProvider,
            String primaryProviderUserId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime emailVerifiedAt,
            LocalDateTime deletedAt
    ) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.createdAt = createdAt;
        this.primaryAuthProvider = primaryAuthProvider;
        this.primaryProviderUserId = primaryProviderUserId;
        this.updatedAt = updatedAt;
        this.emailVerifiedAt = emailVerifiedAt;
        this.deletedAt = deletedAt;
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
        this.updatedAt = LocalDateTime.now();
    }

    public void updateProfile(String displayName, String email, String profileImageUrl, LocalDateTime now) {
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName;
        }
        if (email != null && !email.isBlank()) {
            this.email = email;
        }
        if (profileImageUrl != null && !profileImageUrl.isBlank()) {
            this.profileImageUrl = profileImageUrl;
        }
        this.updatedAt = now;
    }

    public void updatePassword(String passwordHash, LocalDateTime now) {
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    public void prepareLocalSignup(String displayName, String passwordHash, LocalDateTime now) {
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.primaryAuthProvider = AuthProvider.LOCAL;
        this.primaryProviderUserId = null;
        this.emailVerifiedAt = null;
        this.updatedAt = now;
    }

    public void markEmailVerified(LocalDateTime now) {
        this.emailVerifiedAt = now;
        this.updatedAt = now;
    }

    public void markDeleted(String anonymizedUsername, String anonymizedEmail, LocalDateTime now) {
        this.username = anonymizedUsername;
        this.email = anonymizedEmail;
        this.displayName = "탈퇴한 사용자";
        this.profileImageUrl = null;
        this.primaryProviderUserId = null;
        this.emailVerifiedAt = null;
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public void syncPrimaryAuth(AuthProvider authProvider, String providerUserId, LocalDateTime now) {
        this.primaryAuthProvider = authProvider;
        this.primaryProviderUserId = providerUserId;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean canUsePasswordAuth() {
        return primaryAuthProvider == AuthProvider.LOCAL;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }
}
