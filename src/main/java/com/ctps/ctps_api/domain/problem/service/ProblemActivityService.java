package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.entity.ProblemSolveHistoryEntry;
import com.ctps.ctps_api.domain.problem.repository.ProblemSolveHistoryRepository;
import com.ctps.ctps_api.global.time.DateTimeSupport;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProblemActivityService {

    private final ProblemSolveHistoryRepository problemSolveHistoryRepository;
    private final Clock clock;

    public void recordSolveAttempts(
            Problem problem,
            List<LocalDateTime> previousSolveHistory,
            List<LocalDateTime> currentSolveHistory,
            Problem.Result result,
            String memo
    ) {
        if (result == null || currentSolveHistory == null || currentSolveHistory.isEmpty()) {
            return;
        }

        Map<LocalDateTime, Integer> previousCounts = toCountMap(previousSolveHistory);
        LocalDateTime createdAt = DateTimeSupport.nowUtc(clock);
        List<ProblemSolveHistoryEntry> newEntries = currentSolveHistory.stream()
                .filter(solvedAt -> {
                    int remaining = previousCounts.getOrDefault(solvedAt, 0);
                    if (remaining > 0) {
                        previousCounts.put(solvedAt, remaining - 1);
                        return false;
                    }
                    return true;
                })
                .map(solvedAt -> ProblemSolveHistoryEntry.builder()
                        .problem(problem)
                        .user(problem.getUser())
                        .activityType(ProblemSolveHistoryEntry.ActivityType.solve)
                        .result(result)
                        .memo(memo == null ? "" : memo)
                        .solvedAt(solvedAt)
                        .createdAt(createdAt)
                        .build())
                .toList();

        if (!newEntries.isEmpty()) {
            problemSolveHistoryRepository.saveAll(newEntries);
        }
    }

    public void recordReviewCompletion(
            Problem problem,
            Problem.Result result,
            String memo,
            LocalDateTime completedAt
    ) {
        problemSolveHistoryRepository.save(ProblemSolveHistoryEntry.builder()
                .problem(problem)
                .user(problem.getUser())
                .activityType(ProblemSolveHistoryEntry.ActivityType.review)
                .result(result)
                .memo(memo == null ? "" : memo)
                .solvedAt(completedAt)
                .createdAt(DateTimeSupport.nowUtc(clock))
                .build());
    }

    private Map<LocalDateTime, Integer> toCountMap(List<LocalDateTime> history) {
        Map<LocalDateTime, Integer> counts = new HashMap<>();
        if (history == null) {
            return counts;
        }

        for (LocalDateTime solvedAt : history) {
            counts.merge(solvedAt, 1, Integer::sum);
        }
        return counts;
    }
}
