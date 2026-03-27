package com.ctps.ctps_api.domain.search.repository;

import com.ctps.ctps_api.domain.search.entity.SearchEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchEventRepository extends JpaRepository<SearchEvent, Long> {

    List<SearchEvent> findAllByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Long userId, LocalDateTime createdAt);
}
