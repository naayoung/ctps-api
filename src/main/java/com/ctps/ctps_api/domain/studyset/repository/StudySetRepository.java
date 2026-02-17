package com.ctps.ctps_api.domain.studyset.repository;

import com.ctps.ctps_api.domain.studyset.entity.StudySet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySetRepository extends JpaRepository<StudySet, Long> {
}
