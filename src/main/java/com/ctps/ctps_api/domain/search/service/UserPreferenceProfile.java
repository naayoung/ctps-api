package com.ctps.ctps_api.domain.search.service;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserPreferenceProfile {

    private Map<String, Double> recentTagScores;
    private Map<String, Double> recentTypeScores;
    private Map<String, Double> lifetimeTagScores;
    private Map<String, Double> difficultyScores;
    private Map<String, Double> platformScores;

    public static UserPreferenceProfile empty() {
        return UserPreferenceProfile.builder()
                .recentTagScores(Map.of())
                .recentTypeScores(Map.of())
                .lifetimeTagScores(Map.of())
                .difficultyScores(Map.of())
                .platformScores(Map.of())
                .build();
    }

    public List<String> topTagKeys(int limit) {
        return topKeys(recentTagScores, limit);
    }

    public List<String> topTypeKeys(int limit) {
        return topKeys(recentTypeScores, limit);
    }

    public Problem.Difficulty topDifficulty() {
        return difficultyScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .map(key -> {
                    try {
                        return Problem.Difficulty.valueOf(key);
                    } catch (IllegalArgumentException exception) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public String topPlatform() {
        return platformScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private List<String> topKeys(Map<String, Double> source, int limit) {
        return source.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }
}
