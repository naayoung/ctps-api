package com.ctps.ctps_api.domain.search.service;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.search.dto.FrequentSearchTypeItemResponse;
import com.ctps.ctps_api.domain.search.dto.FrequentSearchTypesResponse;
import com.ctps.ctps_api.domain.search.entity.ProblemInteractionEvent;
import com.ctps.ctps_api.domain.search.entity.SearchEvent;
import com.ctps.ctps_api.domain.search.repository.ProblemInteractionEventRepository;
import com.ctps.ctps_api.domain.search.repository.SearchEventRepository;
import com.ctps.ctps_api.global.security.CurrentUserContext;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FrequentSearchTypeService {

    private static final int PERIOD_DAYS = 30;
    private static final int RECENT_DAYS = 7;
    private static final int MAX_ITEMS = 3;
    private static final int MIN_INTERACTION_EVENTS = 2;

    private static final Map<ProblemInteractionEvent.EventType, Double> EVENT_WEIGHTS = new EnumMap<>(ProblemInteractionEvent.EventType.class);

    static {
        EVENT_WEIGHTS.put(ProblemInteractionEvent.EventType.SEARCH_CLICK, 2.0);
        EVENT_WEIGHTS.put(ProblemInteractionEvent.EventType.DETAIL_VIEW, 2.0);
        EVENT_WEIGHTS.put(ProblemInteractionEvent.EventType.BOOKMARK, 3.0);
        EVENT_WEIGHTS.put(ProblemInteractionEvent.EventType.MARK_REVIEW, 3.0);
    }

    private final SearchEventRepository searchEventRepository;
    private final ProblemInteractionEventRepository problemInteractionEventRepository;
    private final SearchTypeCanonicalizer searchTypeCanonicalizer;
    private final Clock clock;

    public FrequentSearchTypesResponse getFrequentTypes() {
        Long userId = CurrentUserContext.getRequired().getId();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime since = now.minusDays(PERIOD_DAYS);

        List<ProblemInteractionEvent> interactionEvents =
                problemInteractionEventRepository.findAllByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(userId, since);
        List<SearchEvent> searchEvents =
                searchEventRepository.findAllByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(userId, since);

        boolean hasEnoughData = interactionEvents.size() >= MIN_INTERACTION_EVENTS;
        List<FrequentSearchTypeItemResponse> items = hasEnoughData
                ? rankTypes(interactionEvents, List.of(), now)
                : List.of();
        List<FrequentSearchTypeItemResponse> focusedItems = hasEnoughData
                ? rankTypes(
                        interactionEvents.stream().filter(event -> !event.getCreatedAt().isBefore(now.minusDays(RECENT_DAYS))).toList(),
                        List.of(),
                        now
                )
                : List.of();
        List<FrequentSearchTypeItemResponse> reviewNeededItems = hasEnoughData
                ? rankTypes(
                        interactionEvents.stream()
                                .filter(event -> event.getEventType() == ProblemInteractionEvent.EventType.MARK_REVIEW)
                                .toList(),
                        List.of(),
                        now
                )
                : List.of();

        return FrequentSearchTypesResponse.builder()
                .generatedAt(now)
                .periodDays(PERIOD_DAYS)
                .items(items)
                .focusedItems(focusedItems)
                .reviewNeededItems(reviewNeededItems)
                .hasEnoughData(hasEnoughData)
                .build();
    }

    private List<FrequentSearchTypeItemResponse> rankTypes(
            List<ProblemInteractionEvent> interactionEvents,
            List<SearchEvent> searchEvents,
            LocalDateTime now
    ) {
        Map<String, ScoreBucket> buckets = new HashMap<>();

        for (ProblemInteractionEvent event : interactionEvents) {
            double weight = EVENT_WEIGHTS.getOrDefault(event.getEventType(), 0.0) * recencyWeight(now, event.getCreatedAt());
            if (weight <= 0.0) {
                continue;
            }

            for (String tag : searchTypeCanonicalizer.canonicalizeTags(event.getTags())) {
                addScore(buckets, key(FrequentSearchTypeItemResponse.Type.TAG, tag), FrequentSearchTypeItemResponse.Type.TAG, tag, tag, weight, event.getCreatedAt());
            }

            String difficultyKey = event.getDifficulty().name();
            addScore(
                    buckets,
                    key(FrequentSearchTypeItemResponse.Type.DIFFICULTY, difficultyKey),
                    FrequentSearchTypeItemResponse.Type.DIFFICULTY,
                    difficultyKey,
                    searchTypeCanonicalizer.toDifficultyLabel(event.getDifficulty()),
                    weight,
                    event.getCreatedAt()
            );
        }

        for (SearchEvent searchEvent : searchEvents) {
            double weight = recencyWeight(now, searchEvent.getCreatedAt());
            for (SearchTypeCanonicalizer.ResolvedType resolvedType : searchTypeCanonicalizer.resolveSearchQuery(searchEvent.getQuery())) {
                addScore(
                        buckets,
                        key(resolvedType.type(), resolvedType.key()),
                        resolvedType.type(),
                        resolvedType.key(),
                        resolvedType.label(),
                        weight,
                        searchEvent.getCreatedAt()
                );
            }
        }

        return buckets.values().stream()
                .sorted(Comparator
                        .comparingInt(ScoreBucket::evidenceCount).reversed()
                        .thenComparing(ScoreBucket::score, Comparator.reverseOrder())
                        .thenComparing(ScoreBucket::latestAt, Comparator.reverseOrder())
                        .thenComparing(ScoreBucket::label))
                .limit(MAX_ITEMS)
                .map(bucket -> FrequentSearchTypeItemResponse.builder()
                        .type(bucket.type())
                        .key(bucket.key())
                        .label(bucket.label())
                        .score(round(bucket.score()))
                        .evidenceCount(bucket.evidenceCount())
                        .lastActivityAt(bucket.latestAt())
                        .build())
                .toList();
    }

    private void addScore(
            Map<String, ScoreBucket> buckets,
            String bucketKey,
            FrequentSearchTypeItemResponse.Type type,
            String key,
            String label,
            double score,
            LocalDateTime occurredAt
    ) {
        buckets.compute(bucketKey, (ignored, existing) -> {
            if (existing == null) {
                return new ScoreBucket(type, key, label, score, 1, occurredAt);
            }
            return new ScoreBucket(
                    existing.type(),
                    existing.key(),
                    existing.label(),
                    existing.score() + score,
                    existing.evidenceCount() + 1,
                    occurredAt.isAfter(existing.latestAt()) ? occurredAt : existing.latestAt()
            );
        });
    }

    private double recencyWeight(LocalDateTime now, LocalDateTime occurredAt) {
        long days = Duration.between(occurredAt, now).toDays();
        return days < RECENT_DAYS ? 1.5 : 1.0;
    }

    private String key(FrequentSearchTypeItemResponse.Type type, String value) {
        return type.name() + ":" + value;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record ScoreBucket(
            FrequentSearchTypeItemResponse.Type type,
            String key,
            String label,
            double score,
            int evidenceCount,
            LocalDateTime latestAt
    ) {
    }
}
