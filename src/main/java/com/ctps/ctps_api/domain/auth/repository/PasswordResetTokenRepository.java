package com.ctps.ctps_api.domain.auth.repository;

import com.ctps.ctps_api.domain.auth.entity.PasswordResetToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findAllByUser_IdAndUsedAtIsNull(Long userId);
}
