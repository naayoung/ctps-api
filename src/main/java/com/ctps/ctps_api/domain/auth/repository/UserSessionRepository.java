package com.ctps.ctps_api.domain.auth.repository;

import com.ctps.ctps_api.domain.auth.entity.UserSession;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<UserSession> findBySessionTokenAndExpiresAtAfter(String sessionToken, LocalDateTime now);

    void deleteBySessionToken(String sessionToken);

    void deleteByUser_Id(Long userId);
}
