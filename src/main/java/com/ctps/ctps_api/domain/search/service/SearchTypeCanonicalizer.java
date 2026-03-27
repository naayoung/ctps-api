package com.ctps.ctps_api.domain.search.service;

import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.search.dto.FrequentSearchTypeItemResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class SearchTypeCanonicalizer {

    private static final Map<Problem.Difficulty, List<String>> DIFFICULTY_ALIASES = Map.of(
            Problem.Difficulty.easy, List.of("easy", "쉬움", "브론즈", "bronze"),
            Problem.Difficulty.medium, List.of("medium", "보통", "실버", "silver"),
            Problem.Difficulty.hard, List.of("hard", "어려움", "골드", "gold", "플래티넘", "platinum", "diamond", "루비", "ruby")
    );

    private static final Map<String, List<String>> TAG_ALIASES = new LinkedHashMap<>();

    static {
        TAG_ALIASES.put("그래프", List.of("그래프", "graph", "다익스트라", "최단경로", "플로이드", "union-find", "유니온파인드"));
        TAG_ALIASES.put("DP", List.of("dp", "동적계획법", "동적 계획법", "다이나믹 프로그래밍", "dynamic programming"));
        TAG_ALIASES.put("브루트포스", List.of("브루트포스", "완전탐색", "완전 탐색", "bruteforce", "brute force"));
        TAG_ALIASES.put("구현", List.of("구현", "implementation", "시뮬레이션", "simulation", "문자열 처리"));
        TAG_ALIASES.put("BFS", List.of("bfs", "너비우선탐색", "너비 우선 탐색"));
        TAG_ALIASES.put("DFS", List.of("dfs", "깊이우선탐색", "깊이 우선 탐색"));
        TAG_ALIASES.put("그리디", List.of("그리디", "greedy"));
        TAG_ALIASES.put("이분탐색", List.of("이분탐색", "이분 탐색", "binary search", "binarysearch"));
        TAG_ALIASES.put("자료구조", List.of("자료구조", "자료 구조", "data structure", "heap", "queue", "stack"));
    }

    public List<String> canonicalizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }

        return rawTags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .map(this::canonicalizeTag)
                .distinct()
                .toList();
    }

    public String canonicalizeTag(String rawTag) {
        String normalized = normalize(rawTag);
        for (Map.Entry<String, List<String>> entry : TAG_ALIASES.entrySet()) {
            if (entry.getValue().stream().map(this::normalize).anyMatch(alias -> alias.equals(normalized))) {
                return entry.getKey();
            }
        }
        return rawTag.trim();
    }

    public List<ResolvedType> resolveSearchQuery(String query) {
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<ResolvedType> resolved = new ArrayList<>();
        for (Map.Entry<Problem.Difficulty, List<String>> entry : DIFFICULTY_ALIASES.entrySet()) {
            if (containsAny(normalized, entry.getValue())) {
                resolved.add(new ResolvedType(
                        FrequentSearchTypeItemResponse.Type.DIFFICULTY,
                        entry.getKey().name(),
                        toDifficultyLabel(entry.getKey())
                ));
            }
        }

        for (Map.Entry<String, List<String>> entry : TAG_ALIASES.entrySet()) {
            if (containsAny(normalized, entry.getValue())) {
                resolved.add(new ResolvedType(FrequentSearchTypeItemResponse.Type.TAG, entry.getKey(), entry.getKey()));
            }
        }

        return resolved;
    }

    public String toDifficultyLabel(Problem.Difficulty difficulty) {
        return switch (difficulty) {
            case easy -> "쉬움";
            case medium -> "보통";
            case hard -> "어려움";
        };
    }

    private boolean containsAny(String normalizedQuery, List<String> aliases) {
        return aliases.stream().map(this::normalize).anyMatch(normalizedQuery::contains);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record ResolvedType(FrequentSearchTypeItemResponse.Type type, String key, String label) {
    }
}
