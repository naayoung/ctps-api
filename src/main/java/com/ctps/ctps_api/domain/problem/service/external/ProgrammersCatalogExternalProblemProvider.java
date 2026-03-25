package com.ctps.ctps_api.domain.problem.service.external;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.repository.ProgrammersProblemCatalogRepository;
import com.ctps.ctps_api.domain.problem.service.ExternalProblemProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProgrammersCatalogExternalProblemProvider implements ExternalProblemProvider {

    private final ProgrammersProblemCatalogRepository catalogRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<ExternalProblemSearchItemResponse> search(ProblemSearchRequest request) {
        String keyword = request.getKeyword() == null ? "" : request.getKeyword().toLowerCase(Locale.ROOT);

        return catalogRepository.findAll().stream()
                .filter(item -> keyword.isBlank()
                        || contains(item.getTitle(), keyword)
                        || readTags(item.getTagsJson()).stream().anyMatch(tag -> contains(tag, keyword)))
                .filter(item -> request.getDifficulty().isEmpty()
                        || request.getDifficulty().contains(parseDifficulty(item.getDifficulty())))
                .filter(item -> request.getTags().isEmpty()
                        || request.getTags().stream().allMatch(tag ->
                        readTags(item.getTagsJson()).stream().anyMatch(itemTag -> contains(itemTag, tag))))
                .limit(12)
                .map(item -> ExternalProblemSearchItemResponse.builder()
                        .id(item.getExternalId())
                        .providerKey(providerKey())
                        .providerLabel(providerLabel())
                        .title(item.getTitle())
                        .platform("프로그래머스")
                        .problemNumber(item.getProblemNumber())
                        .difficulty(parseDifficulty(item.getDifficulty()))
                        .tags(readTags(item.getTagsJson()))
                        .externalUrl(item.getExternalUrl())
                        .summary("프로그래머스 카탈로그에서 적재한 외부 문제")
                        .recommendationReason(item.getRecommendationReason())
                        .solved(false)
                        .build())
                .toList();
    }

    @Override
    public String providerKey() {
        return "programmers";
    }

    @Override
    public String providerLabel() {
        return "프로그래머스";
    }

    private List<String> readTags(String tagsJson) {
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<>() {
            });
        } catch (Exception exception) {
            log.warn("failed to parse programmers tags json", exception);
            return List.of();
        }
    }

    private Problem.Difficulty parseDifficulty(String difficulty) {
        return switch (difficulty == null ? "" : difficulty.toLowerCase(Locale.ROOT)) {
            case "easy" -> Problem.Difficulty.easy;
            case "hard" -> Problem.Difficulty.hard;
            default -> Problem.Difficulty.medium;
        };
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
}
