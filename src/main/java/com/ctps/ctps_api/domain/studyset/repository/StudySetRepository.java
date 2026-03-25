package com.ctps.ctps_api.domain.studyset.repository;

import com.ctps.ctps_api.domain.studyset.entity.StudySet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySetRepository extends JpaRepository<StudySet, Long> {

    List<StudySet> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<StudySet> findByIdAndUserId(Long id, Long userId);
}
