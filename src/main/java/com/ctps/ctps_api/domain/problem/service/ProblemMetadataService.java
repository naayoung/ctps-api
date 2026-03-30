package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResolveRequest;
import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResponse;
import com.ctps.ctps_api.domain.problem.service.metadata.ProblemMetadataResolver;
import com.ctps.ctps_api.domain.problem.service.metadata.ProblemMetadataSupport;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemMetadataService {

    private final List<ProblemMetadataResolver> resolvers;

    public ProblemMetadataResponse resolve(ProblemMetadataResolveRequest request) {
        Map<String, ProblemMetadataResolver> resolversByPlatform = resolvers.stream()
                .collect(Collectors.toMap(ProblemMetadataResolver::platform, Function.identity()));
        ResolvedProblemRef ref = ResolvedProblemRef.from(request);
        if (!StringUtils.hasText(ref.platform()) && StringUtils.hasText(ref.link())) {
            ref = inferFromLink(ref.link(), ref);
        }

        String platform = normalizePlatform(ref.platform());
        String number = normalizeNumber(ref.number());
        String link = normalizeLink(ref.link(), platform, number);

        if (!StringUtils.hasText(platform) || !StringUtils.hasText(number)) {
            return ProblemMetadataResponse.builder()
                    .platform(platform)
                    .number(number)
                    .link(link)
                    .tags(List.of())
                    .metadataFound(false)
                    .build();
        }

        try {
            ProblemMetadataResolver resolver = resolversByPlatform.get(platform);
            if (resolver == null) {
                return ProblemMetadataSupport.notFound(platform, number, link);
            }
            return resolver.resolve(number, link);
        } catch (Exception exception) {
            log.warn("problem metadata resolve failed platform={} number={} link={}", platform, number, link, exception);
            return ProblemMetadataSupport.notFound(platform, number, link);
        }
    }

    private String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return null;
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("백준") || normalized.equals("boj") || normalized.contains("baekjoon")) {
            return ProblemMetadataSupport.BAEKJOON_PLATFORM;
        }
        if (normalized.contains("프로그래머스") || normalized.contains("programmers")) {
            return ProblemMetadataSupport.PROGRAMMERS_PLATFORM;
        }
        if (normalized.contains("리트코드") || normalized.contains("leetcode")) {
            return ProblemMetadataSupport.LEETCODE_PLATFORM;
        }
        return platform.trim();
    }

    private String normalizeNumber(String number) {
        if (!StringUtils.hasText(number)) {
            return null;
        }
        return number.trim();
    }

    private String normalizeLink(String link, String platform, String number) {
        if (StringUtils.hasText(link)) {
            return link.trim();
        }
        return ProblemMetadataSupport.buildCanonicalLink(platform, number, null);
    }

    private ResolvedProblemRef inferFromLink(String link, ResolvedProblemRef fallback) {
        try {
            URI uri = URI.create(link.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (host.contains("acmicpc.net")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/problem/(\\d+)").matcher(path);
                if (matcher.find()) {
                    return new ResolvedProblemRef(ProblemMetadataSupport.BAEKJOON_PLATFORM, matcher.group(1), link.trim());
                }
            }
            if (host.contains("programmers.co.kr") || host.contains("school.programmers.co.kr")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/learn/courses/\\d+/lessons/(\\d+)").matcher(path);
                if (matcher.find()) {
                    return new ResolvedProblemRef(ProblemMetadataSupport.PROGRAMMERS_PLATFORM, matcher.group(1), link.trim());
                }
            }
            if (host.contains("leetcode.com")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/problems/([^/]+)").matcher(path);
                if (matcher.find()) {
                    return new ResolvedProblemRef(ProblemMetadataSupport.LEETCODE_PLATFORM, matcher.group(1), link.trim());
                }
            }
        } catch (Exception exception) {
            log.debug("problem metadata link parse failed link={}", link, exception);
        }
        return fallback;
    }

    private record ResolvedProblemRef(String platform, String number, String link) {
        private static ResolvedProblemRef from(ProblemMetadataResolveRequest request) {
            return new ResolvedProblemRef(request.getPlatform(), request.getNumber(), request.getLink());
        }
    }
}
