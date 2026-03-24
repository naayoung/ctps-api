package com.ctps.ctps_api.domain.problem.dto.search;

public enum ProblemSearchSortOption {
    RELEVANCE,
    LAST_SOLVED_AT,
    DIFFICULTY,
    CREATED_AT,
    PROBLEM_NUMBER;

    public static ProblemSearchSortOption from(String sort) {
        if (sort == null || sort.isBlank()) {
            return RELEVANCE;
        }

        String property = sort.split(",")[0].trim();

        return switch (property) {
            case "lastSolvedAt" -> LAST_SOLVED_AT;
            case "difficulty" -> DIFFICULTY;
            case "createdAt" -> CREATED_AT;
            case "problemNumber", "number" -> PROBLEM_NUMBER;
            case "relevance" -> RELEVANCE;
            default -> RELEVANCE;
        };
    }
}
