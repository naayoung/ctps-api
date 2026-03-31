package com.ctps.ctps_api.domain.search.service;

import com.ctps.ctps_api.domain.search.entity.ProblemInteractionEvent;
import com.ctps.ctps_api.domain.search.repository.ProblemInteractionEventRepository;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPreferenceAnalyzer {

    private static final int RECENT_DAYS = 30;
    private static final Map<ProblemInteractionEvent.EventType, Double> EVENT_WEIGHTS =
            new EnumMap<>(ProblemInteractionEvent.EventType.class);

    static {
        EVENT_WEIGHTS.put(ProblemInteractionEvent.EventType.SEARCH_CLICK, 1.0);
        EVENT_WEIGHTS.put(ProblemInteractionEvent.EventType.DETAIL_VIEW, 1.2);
        EVENT_WEIGHTS.put(ProblemInteractionEvent.EventType.BOOKMARK, 1.5);
        EVENT_WEIGHTS.put(ProblemInteractionEvent.EventType.MARK_REVIEW, 1.4);
    }

    private final ProblemInteractionEventRepository problemInteractionEventRepository;
    private final SearchTypeCanonicalizer searchTypeCanonicalizer;
    private final Clock clock;

    public UserPreferenceProfile analyze() {
        Long userId = CurrentUserContext.getOptional().map(user -> user.getId()).orElse(null);
        if (userId == null) {
            return UserPreferenceProfile.empty();
        }

        List<ProblemInteractionEvent> events = problemInteractionEventRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        if (events.isEmpty()) {
            return UserPreferenceProfile.empty();
        }

        LocalDateTime recentThreshold = LocalDateTime.now(clock).minusDays(RECENT_DAYS);

        Map<String, Double> recentTagScores = new HashMap<>();
        Map<String, Double> recentTypeScores = new HashMap<>();
        Map<String, Double> lifetimeTagScores = new HashMap<>();
        Map<String, Double> difficultyScores = new HashMap<>();
        Map<String, Double> platformScores = new HashMap<>();

        for (ProblemInteractionEvent event : events) {
            double eventWeight = EVENT_WEIGHTS.getOrDefault(event.getEventType(), 1.0);
            boolean recent = !event.getCreatedAt().isBefore(recentThreshold);
            double recentBoost = recent ? 1.5 : 1.0;

            for (String tag : searchTypeCanonicalizer.canonicalizeTags(event.getTags())) {
                addScore(lifetimeTagScores, tag, eventWeight);
                if (recent) {
                    addScore(recentTagScores, tag, eventWeight * recentBoost);
                    addScore(recentTypeScores, tag, eventWeight * recentBoost);
                }
            }

            if (event.getDifficulty() != null) {
                addScore(difficultyScores, event.getDifficulty().name(), eventWeight * recentBoost);
            }

            if (StringUtils.hasText(event.getPlatform())) {
                addScore(platformScores, event.getPlatform().trim().toLowerCase(java.util.Locale.ROOT), eventWeight * recentBoost);
            }
        }

        return UserPreferenceProfile.builder()
                .recentTagScores(normalizeScores(recentTagScores))
                .recentTypeScores(normalizeScores(recentTypeScores))
                .lifetimeTagScores(normalizeScores(lifetimeTagScores))
                .difficultyScores(normalizeScores(difficultyScores))
                .platformScores(normalizeScores(platformScores))
                .build();
    }

    private void addScore(Map<String, Double> scores, String key, double value) {
        if (!StringUtils.hasText(key) || value <= 0) {
            return;
        }
        scores.merge(key.trim().toLowerCase(java.util.Locale.ROOT), value, Double::sum);
    }

    private Map<String, Double> normalizeScores(Map<String, Double> scores) {
        double max = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        if (max <= 0.0) {
            return Map.of();
        }
        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / max);
        }
        return Map.copyOf(normalized);
    }
}
