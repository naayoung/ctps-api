package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.dto.admin.ExternalSearchMetricsResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ExternalSearchMetricsService {

    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> providerSuccesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> providerFailures = new ConcurrentHashMap<>();

    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    public void recordProviderSuccess(String provider) {
        providerSuccesses.computeIfAbsent(provider, key -> new AtomicLong()).incrementAndGet();
    }

    public void recordProviderFailure(String provider) {
        providerFailures.computeIfAbsent(provider, key -> new AtomicLong()).incrementAndGet();
    }

    public ExternalSearchMetricsResponse snapshot() {
        return ExternalSearchMetricsResponse.builder()
                .cacheHits(cacheHits.get())
                .cacheMisses(cacheMisses.get())
                .providerSuccesses(toMap(providerSuccesses))
                .providerFailures(toMap(providerFailures))
                .build();
    }

    @Scheduled(cron = "${external.search.metrics.log-cron:0 */30 * * * *}")
    public void logSnapshot() {
        ExternalSearchMetricsResponse snapshot = snapshot();
        log.info(
                "external-search metrics cacheHits={} cacheMisses={} providerSuccesses={} providerFailures={}",
                snapshot.getCacheHits(),
                snapshot.getCacheMisses(),
                snapshot.getProviderSuccesses(),
                snapshot.getProviderFailures()
        );
    }

    private Map<String, Long> toMap(ConcurrentHashMap<String, AtomicLong> source) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
    }
}
