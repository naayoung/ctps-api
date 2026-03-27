package com.ctps.ctps_api.domain.search.service;

import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.auth.repository.UserRepository;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.search.dto.ProblemInteractionEventRequest;
import com.ctps.ctps_api.domain.search.dto.SearchEventRequest;
import com.ctps.ctps_api.domain.search.dto.SearchItemSource;
import com.ctps.ctps_api.domain.search.entity.ProblemInteractionEvent;
import com.ctps.ctps_api.domain.search.entity.SearchEvent;
import com.ctps.ctps_api.domain.search.repository.ProblemInteractionEventRepository;
import com.ctps.ctps_api.domain.search.repository.SearchEventRepository;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SearchActivityService {

    private final SearchEventRepository searchEventRepository;
    private final ProblemInteractionEventRepository problemInteractionEventRepository;
    private final UserRepository userRepository;
    private final SearchTypeCanonicalizer searchTypeCanonicalizer;
    private final Clock clock;

    public void recordSearchEvent(SearchEventRequest request) {
        String query = request.getQuery().trim();
        if (query.isBlank()) {
            return;
        }

        searchEventRepository.save(SearchEvent.builder()
                .user(currentUser())
                .query(query)
                .createdAt(LocalDateTime.now(clock))
                .build());
    }

    public void recordInteractionEvent(ProblemInteractionEventRequest request) {
        problemInteractionEventRepository.save(ProblemInteractionEvent.builder()
                .user(currentUser())
                .problemRef(request.getProblemRef().trim())
                .source(request.getSource())
                .platform(request.getPlatform().trim())
                .problemNumber(request.getProblemNumber().trim())
                .difficulty(request.getDifficulty())
                .tags(searchTypeCanonicalizer.canonicalizeTags(request.getTags()))
                .eventType(request.getEventType())
                .sourceQuery(normalizeNullable(request.getSourceQuery()))
                .createdAt(LocalDateTime.now(clock))
                .build());
    }

    public void recordBookmarkEvent(Problem problem) {
        saveProblemInteraction(problem, ProblemInteractionEvent.EventType.BOOKMARK, null);
    }

    public void recordMarkReviewEvent(Problem problem) {
        saveProblemInteraction(problem, ProblemInteractionEvent.EventType.MARK_REVIEW, null);
    }

    private void saveProblemInteraction(Problem problem, ProblemInteractionEvent.EventType eventType, String sourceQuery) {
        problemInteractionEventRepository.save(ProblemInteractionEvent.builder()
                .user(currentUser())
                .problemRef(String.valueOf(problem.getId()))
                .source(SearchItemSource.INTERNAL)
                .platform(problem.getPlatform())
                .problemNumber(problem.getNumber())
                .difficulty(problem.getDifficulty())
                .tags(searchTypeCanonicalizer.canonicalizeTags(problem.getTags()))
                .eventType(eventType)
                .sourceQuery(normalizeNullable(sourceQuery))
                .createdAt(LocalDateTime.now(clock))
                .build());
    }

    private User currentUser() {
        return userRepository.getReferenceById(CurrentUserContext.getRequired().getId());
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
