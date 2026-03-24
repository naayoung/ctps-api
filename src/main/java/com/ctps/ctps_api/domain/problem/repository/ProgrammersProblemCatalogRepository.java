package com.ctps.ctps_api.domain.problem.repository;

import com.ctps.ctps_api.domain.problem.entity.ProgrammersProblemCatalog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgrammersProblemCatalogRepository extends JpaRepository<ProgrammersProblemCatalog, Long> {

    Optional<ProgrammersProblemCatalog> findByExternalId(String externalId);
}
