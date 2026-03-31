package com.ctps.ctps_api.domain.problem.service.metadata;

import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResponse;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.service.external.LeetCodeExternalProblemProvider.LeetCodeDifficulty;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

public final class ProblemMetadataSupport {

    public static final String BAEKJOON_PLATFORM = "백준";
    public static final String PROGRAMMERS_PLATFORM = "프로그래머스";
    public static final String LEETCODE_PLATFORM = "리트코드";

    public static final String SOLVED_AC_BASE_URL = "https://solved.ac";
    public static final String PROGRAMMERS_BASE_URL = "https://school.programmers.co.kr";
    public static final String LEETCODE_BASE_URL = "https://leetcode.com";

    private ProblemMetadataSupport() {
    }

    public static ProblemMetadataResponse notFound(String platform, String number, String link) {
        return ProblemMetadataResponse.builder()
                .platform(platform)
                .number(number)
                .link(link)
                .tags(List.of())
                .metadataFound(false)
                .build();
    }

    public static String buildCanonicalLink(String platform, String number, String fallbackLink) {
        if (!StringUtils.hasText(platform) || !StringUtils.hasText(number)) {
            return fallbackLink;
        }
        return switch (platform) {
            case BAEKJOON_PLATFORM -> "https://www.acmicpc.net/problem/" + number;
            case PROGRAMMERS_PLATFORM -> "https://school.programmers.co.kr/learn/courses/30/lessons/" + number;
            case LEETCODE_PLATFORM -> "https://leetcode.com/problems/" + number + "/";
            default -> fallbackLink;
        };
    }

    public static Problem.Difficulty parseProgrammersDifficulty(String rawDifficulty) {
        if (!StringUtils.hasText(rawDifficulty)) {
            return null;
        }
        String normalized = rawDifficulty.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("\\d+")) {
            int level = Integer.parseInt(normalized);
            if (level <= 1) {
                return Problem.Difficulty.easy;
            }
            if (level <= 3) {
                return Problem.Difficulty.medium;
            }
            return Problem.Difficulty.hard;
        }
        if (normalized.contains("lv. 0") || normalized.contains("lv.0") || normalized.contains("level 0")
                || normalized.contains("lv. 1") || normalized.contains("lv.1") || normalized.contains("level 1")) {
            return Problem.Difficulty.easy;
        }
        if (normalized.contains("lv. 2") || normalized.contains("lv.2") || normalized.contains("level 2")
                || normalized.contains("lv. 3") || normalized.contains("lv.3") || normalized.contains("level 3")) {
            return Problem.Difficulty.medium;
        }
        return Problem.Difficulty.hard;
    }

    public static Problem.Difficulty mapBaekjoonDifficulty(Integer level) {
        int safeLevel = level == null ? 0 : level;
        if (safeLevel >= 11) {
            return Problem.Difficulty.hard;
        }
        if (safeLevel >= 6) {
            return Problem.Difficulty.medium;
        }
        return Problem.Difficulty.easy;
    }

    public static Problem.Difficulty mapLeetCodeDifficulty(LeetCodeDifficulty difficulty) {
        int level = difficulty == null ? 0 : difficulty.getLevel();
        return switch (level) {
            case 1 -> Problem.Difficulty.easy;
            case 2 -> Problem.Difficulty.medium;
            default -> Problem.Difficulty.hard;
        };
    }

    public static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }
}
