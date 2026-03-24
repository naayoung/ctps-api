package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.dto.external.ExternalProblemSearchItemResponse;
import com.ctps.ctps_api.domain.problem.entity.ExternalProblemCache;
import com.ctps.ctps_api.domain.problem.repository.ExternalProblemCacheRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalProblemCacheService {

    private final ExternalProblemCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;

    @Value("${external.search.cache.ttl-minutes:180}")
    private long cacheTtlMinutes;

    public CachedExternalProblemResult findValidCache(String provider, String queryKey) {
        String queryHash = hash(queryKey);
        return cacheRepository.findByProviderAndQueryHash(provider, queryHash)
                .filter(cache -> cache.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(this::toCachedResult)
                .orElse(null);
    }

    @Transactional
    public void save(
            String provider,
            String queryKey,
            List<ExternalProblemSearchItemResponse> items,
            long totalElements,
            int totalPages
    ) {
        try {
            String queryHash = hash(queryKey);
            String contentJson = objectMapper.writeValueAsString(items);
            LocalDateTime fetchedAt = LocalDateTime.now();
            LocalDateTime expiresAt = fetchedAt.plusMinutes(cacheTtlMinutes);

            ExternalProblemCache cache = cacheRepository.findByProviderAndQueryHash(provider, queryHash)
                    .orElseGet(() -> ExternalProblemCache.builder()
                            .provider(provider)
                            .queryKey(queryKey)
                            .queryHash(queryHash)
                            .contentJson(contentJson)
                            .totalElements(totalElements)
                            .totalPages(totalPages)
                            .fetchedAt(fetchedAt)
                            .expiresAt(expiresAt)
                            .build());

            cache.refresh(contentJson, totalElements, totalPages, fetchedAt, expiresAt);
            cacheRepository.save(cache);
        } catch (Exception exception) {
            log.warn("failed to save external problem cache", exception);
        }
    }

    private CachedExternalProblemResult toCachedResult(ExternalProblemCache cache) {
        try {
            List<ExternalProblemSearchItemResponse> items = objectMapper.readValue(
                    cache.getContentJson(),
                    new TypeReference<>() {
                    }
            );
            return new CachedExternalProblemResult(items, cache.getTotalElements(), cache.getTotalPages());
        } catch (Exception exception) {
            log.warn("failed to read external problem cache", exception);
            return new CachedExternalProblemResult(List.of(), 0, 0);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to hash query key", exception);
        }
    }

    public record CachedExternalProblemResult(
            List<ExternalProblemSearchItemResponse> items,
            long totalElements,
            int totalPages
    ) {
    }
}
