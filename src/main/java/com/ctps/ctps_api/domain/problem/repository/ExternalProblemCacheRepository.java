package com.ctps.ctps_api.domain.problem.repository;

import com.ctps.ctps_api.domain.problem.entity.ExternalProblemCache;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalProblemCacheRepository extends JpaRepository<ExternalProblemCache, Long> {

    Optional<ExternalProblemCache> findByProviderAndQueryHash(String provider, String queryHash);

    List<ExternalProblemCache> findAllByExpiresAtBefore(LocalDateTime now);
}
