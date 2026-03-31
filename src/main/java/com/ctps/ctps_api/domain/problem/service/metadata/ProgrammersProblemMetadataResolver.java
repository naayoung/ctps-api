package com.ctps.ctps_api.domain.problem.service.metadata;

import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResponse;
import com.ctps.ctps_api.domain.problem.entity.Problem;
import com.ctps.ctps_api.domain.problem.entity.ProgrammersProblemCatalog;
import com.ctps.ctps_api.domain.problem.repository.ProgrammersProblemCatalogRepository;
import com.ctps.ctps_api.global.config.ExternalProviderRestClientFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ProgrammersProblemMetadataResolver implements ProblemMetadataResolver {

    private static final Pattern NEXT_DATA_PATTERN =
            Pattern.compile("<script[^>]*id=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern HTML_TITLE_PATTERN =
            Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OG_TITLE_PATTERN =
            Pattern.compile("<meta[^>]*property=[\"']og:title[\"'][^>]*content=[\"'](.*?)[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LESSON_TITLE_DATA_PATTERN =
            Pattern.compile("data-lesson-title=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CHALLENGE_LEVEL_PATTERN =
            Pattern.compile("data-challenge-level=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BREADCRUMB_PATTERN =
            Pattern.compile("<ol[^>]*class=[\"'][^\"']*breadcrumb[^\"']*[\"'][^>]*>(.*?)</ol>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TEXT_PATTERN = Pattern.compile("<[^>]+>");

    private final ExternalProviderRestClientFactory restClientFactory;
    private final ProgrammersProblemCatalogRepository programmersProblemCatalogRepository;
    private final ObjectMapper objectMapper;

    public ProgrammersProblemMetadataResolver(
            ExternalProviderRestClientFactory restClientFactory,
            ProgrammersProblemCatalogRepository programmersProblemCatalogRepository,
            ObjectMapper objectMapper
    ) {
        this.restClientFactory = restClientFactory;
        this.programmersProblemCatalogRepository = programmersProblemCatalogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String platform() {
        return ProblemMetadataSupport.PROGRAMMERS_PLATFORM;
    }

    @Override
    public ProblemMetadataResponse resolve(String number, String link) {
        String canonicalLink = ProblemMetadataSupport.buildCanonicalLink(platform(), number, link);
        ProgrammersProblemCatalog item = programmersProblemCatalogRepository.findFirstByProblemNumber(number)
                .or(() -> programmersProblemCatalogRepository.findFirstByExternalUrl(canonicalLink))
                .orElse(null);

        if (item != null) {
            return ProblemMetadataResponse.builder()
                    .platform(platform())
                    .number(item.getProblemNumber())
                    .link(item.getExternalUrl())
                    .title(item.getTitle())
                    .tags(readProgrammersTags(item.getTagsJson()))
                    .difficulty(ProblemMetadataSupport.parseProgrammersDifficulty(item.getDifficulty()))
                    .metadataFound(true)
                    .build();
        }

        return resolveFromPage(number, canonicalLink);
    }

    private List<String> readProgrammersTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception exception) {
            log.debug("programmers tags parse failed", exception);
            return List.of();
        }
    }

    private ProblemMetadataResponse resolveFromPage(String number, String link) {
        RestClient restClient = restClientFactory.create(ProblemMetadataSupport.PROGRAMMERS_BASE_URL);
        String html = restClient.get()
                .uri(URI.create(link))
                .header("User-Agent", "ctps-problem-metadata/1.0")
                .accept(MediaType.TEXT_HTML, MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        if (!StringUtils.hasText(html)) {
            return ProblemMetadataSupport.notFound(platform(), number, link);
        }

        ProgrammersPageMetadata metadata = extractProgrammersPageMetadata(number, html);
        if (!metadata.metadataFound()) {
            return ProblemMetadataSupport.notFound(platform(), number, link);
        }

        return ProblemMetadataResponse.builder()
                .platform(platform())
                .number(number)
                .link(link)
                .title(metadata.title())
                .tags(metadata.tags())
                .difficulty(metadata.difficulty())
                .metadataFound(true)
                .build();
    }

    private ProgrammersPageMetadata extractProgrammersPageMetadata(String number, String html) {
        Set<String> resolvedTags = new LinkedHashSet<>();
        String resolvedTitle = "";
        String resolvedDifficulty = "";

        try {
            Matcher matcher = NEXT_DATA_PATTERN.matcher(html);
            if (matcher.find()) {
                JsonNode root = objectMapper.readTree(matcher.group(1));
                for (JsonNode candidate : walkNodes(root)) {
                    String identifier = firstPresent(candidate, List.of("lessonId", "lesson_id", "problemId", "id"));
                    String candidateTitle = ProblemMetadataSupport.normalizeWhitespace(
                            firstPresent(candidate, List.of("title", "name", "lessonTitle"))
                    );
                    boolean sameLesson = StringUtils.hasText(identifier) && identifier.equals(number);
                    if (!sameLesson && !StringUtils.hasText(candidateTitle)) {
                        continue;
                    }

                    if (sameLesson && !StringUtils.hasText(resolvedTitle) && StringUtils.hasText(candidateTitle)) {
                        resolvedTitle = candidateTitle;
                    }
                    if (sameLesson && !StringUtils.hasText(resolvedDifficulty)) {
                        resolvedDifficulty = firstPresent(candidate, List.of("difficulty", "level", "difficultyLevel"));
                    }
                    if (sameLesson) {
                        collectEmbeddedTags(candidate, resolvedTags);
                    }
                }
            }
        } catch (Exception exception) {
            log.debug("programmers page metadata parse failed number={}", number, exception);
        }

        if (!StringUtils.hasText(resolvedTitle)) {
            resolvedTitle = extractLessonTitle(html);
        }
        if (!StringUtils.hasText(resolvedDifficulty)) {
            resolvedDifficulty = extractChallengeLevel(html);
        }
        if (resolvedTags.isEmpty()) {
            resolvedTags.addAll(extractBreadcrumbTags(html, resolvedTitle));
        }
        if (!StringUtils.hasText(resolvedTitle)) {
            resolvedTitle = extractHtmlTitle(html);
        }

        Problem.Difficulty difficulty = ProblemMetadataSupport.parseProgrammersDifficulty(resolvedDifficulty);
        List<String> tags = List.copyOf(resolvedTags);
        boolean metadataFound = StringUtils.hasText(resolvedTitle) || !tags.isEmpty() || difficulty != null;
        return new ProgrammersPageMetadata(resolvedTitle, tags, difficulty, metadataFound);
    }

    private void collectEmbeddedTags(JsonNode candidate, Set<String> tags) {
        for (String fieldName : List.of("tags", "skills", "categories", "categoryNames")) {
            JsonNode rawValue = candidate.get(fieldName);
            normalizeEmbeddedTags(rawValue).forEach(tags::add);
        }
    }

    private List<String> normalizeEmbeddedTags(JsonNode rawValue) {
        if (rawValue == null || rawValue.isNull()) {
            return List.of();
        }

        Set<String> tags = new LinkedHashSet<>();
        if (rawValue.isTextual()) {
            tags.addAll(expandTags(rawValue.asText("")));
        } else if (rawValue.isArray()) {
            for (JsonNode entry : rawValue) {
                if (entry.isTextual()) {
                    tags.addAll(expandTags(entry.asText("")));
                    continue;
                }
                if (entry.isObject()) {
                    String value = firstPresent(entry, List.of("name", "label", "title", "value"));
                    if (StringUtils.hasText(value)) {
                        tags.addAll(expandTags(value));
                    }
                }
            }
        }
        return List.copyOf(tags);
    }

    private List<String> expandTags(String rawValue) {
        String cleaned = ProblemMetadataSupport.normalizeWhitespace(rawValue);
        if (!StringUtils.hasText(cleaned)) {
            return List.of();
        }

        Set<String> tags = new LinkedHashSet<>();
        tags.addAll(expandCompositeBreadcrumb(cleaned));
        for (String part : cleaned.split("[|,>]")) {
            for (String nestedPart : part.split("/")) {
                String normalized = ProblemMetadataSupport.normalizeWhitespace(nestedPart);
                if (StringUtils.hasText(normalized)) {
                    tags.add(normalized);
                }
            }
        }
        tags.add(cleaned);
        String withoutAliases = ProblemMetadataSupport.normalizeWhitespace(cleaned.replaceAll("\\([^)]*\\)", ""));
        if (StringUtils.hasText(withoutAliases)) {
            tags.add(withoutAliases);
        }

        Matcher aliasMatcher = Pattern.compile("\\(([^)]+)\\)").matcher(cleaned);
        while (aliasMatcher.find()) {
            for (String alias : aliasMatcher.group(1).split("/")) {
                String normalized = ProblemMetadataSupport.normalizeWhitespace(alias);
                if (StringUtils.hasText(normalized)) {
                    tags.add(normalized);
                }
            }
        }
        return List.copyOf(tags);
    }

    private List<String> expandCompositeBreadcrumb(String cleaned) {
        Matcher matcher = Pattern.compile("^([^/()]+)/([^()]+?)\\(([^/()]+)/([^()]+)\\)$").matcher(cleaned);
        if (!matcher.matches()) {
            return List.of();
        }

        String left = ProblemMetadataSupport.normalizeWhitespace(matcher.group(1));
        String right = ProblemMetadataSupport.normalizeWhitespace(matcher.group(2));
        String leftAlias = ProblemMetadataSupport.normalizeWhitespace(matcher.group(3));
        String rightAlias = ProblemMetadataSupport.normalizeWhitespace(matcher.group(4));
        Set<String> tags = new LinkedHashSet<>();

        tags.add(cleaned);
        tags.add(left);
        tags.add(right);
        tags.add(leftAlias);
        tags.add(rightAlias);

        String sharedSuffix = findSharedSuffix(left, right);
        if (StringUtils.hasText(sharedSuffix)) {
            tags.add(ProblemMetadataSupport.normalizeWhitespace(left + " " + sharedSuffix));
            tags.add(ProblemMetadataSupport.normalizeWhitespace(right));
        }

        return List.copyOf(tags);
    }

    private String findSharedSuffix(String left, String right) {
        String[] leftTokens = left.split(" ");
        String[] rightTokens = right.split(" ");
        if (leftTokens.length != 1 || rightTokens.length < 2) {
            return null;
        }
        return String.join(" ", java.util.Arrays.copyOfRange(rightTokens, 1, rightTokens.length));
    }

    private List<JsonNode> walkNodes(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        walkNodes(node, result);
        return result;
    }

    private void walkNodes(JsonNode node, List<JsonNode> result) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                walkNodes(item, result);
            }
            return;
        }
        if (node.isObject()) {
            result.add(node);
            node.elements().forEachRemaining(child -> walkNodes(child, result));
        }
    }

    private String firstPresent(JsonNode candidate, List<String> keys) {
        for (String key : keys) {
            JsonNode value = candidate.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isArray() && value.isEmpty()) {
                continue;
            }
            if (value.isValueNode()) {
                String text = ProblemMetadataSupport.normalizeWhitespace(value.asText(""));
                if (StringUtils.hasText(text)) {
                    return text;
                }
                continue;
            }
            if (value.isObject()) {
                return value.toString();
            }
        }
        return null;
    }

    private String extractHtmlTitle(String html) {
        for (Pattern pattern : List.of(OG_TITLE_PATTERN, HTML_TITLE_PATTERN)) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String title = ProblemMetadataSupport.normalizeWhitespace(matcher.group(1));
                title = title.replace("코딩테스트 연습 - ", "");
                title = title.replace(" | 프로그래머스 스쿨", "");
                title = title.replace(" - 프로그래머스 스쿨", "");
                if (StringUtils.hasText(title)) {
                    return title;
                }
            }
        }
        return null;
    }

    private String extractLessonTitle(String html) {
        Matcher matcher = LESSON_TITLE_DATA_PATTERN.matcher(html);
        if (!matcher.find()) {
            return null;
        }

        String title = ProblemMetadataSupport.normalizeWhitespace(matcher.group(1));
        return StringUtils.hasText(title) ? title : null;
    }

    private String extractChallengeLevel(String html) {
        Matcher matcher = CHALLENGE_LEVEL_PATTERN.matcher(html);
        if (!matcher.find()) {
            return null;
        }

        String level = ProblemMetadataSupport.normalizeWhitespace(matcher.group(1));
        return StringUtils.hasText(level) ? level : null;
    }

    private List<String> extractBreadcrumbTags(String html, String resolvedTitle) {
        Matcher matcher = BREADCRUMB_PATTERN.matcher(html);
        if (!matcher.find()) {
            return List.of();
        }

        String breadcrumbHtml = matcher.group(1);
        Set<String> tags = new LinkedHashSet<>();
        Matcher anchorMatcher = Pattern.compile("<a[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(breadcrumbHtml);

        while (anchorMatcher.find()) {
            String text = ProblemMetadataSupport.normalizeWhitespace(
                    HTML_TEXT_PATTERN.matcher(anchorMatcher.group(1)).replaceAll(" ")
            );
            if (!StringUtils.hasText(text) || "코딩테스트 연습".equals(text) || text.equals(resolvedTitle)) {
                continue;
            }
            tags.addAll(expandTags(text));
        }

        return List.copyOf(tags);
    }

    private record ProgrammersPageMetadata(
            String title,
            List<String> tags,
            Problem.Difficulty difficulty,
            boolean metadataFound
    ) {
    }
}
