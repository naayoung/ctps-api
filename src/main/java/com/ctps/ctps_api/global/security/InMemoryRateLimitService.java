package com.ctps.ctps_api.global.security;

import com.ctps.ctps_api.global.exception.TooManyRequestsException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InMemoryRateLimitService {

    private final Map<String, ArrayDeque<Instant>> buckets = new ConcurrentHashMap<>();

    public void check(String key, int limit, Duration window, String message) {
        Instant now = Instant.now();
        ArrayDeque<Instant> bucket = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (bucket) {
            Instant threshold = now.minus(window);
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(threshold)) {
                bucket.pollFirst();
            }

            if (bucket.size() >= limit) {
                log.warn("rate_limit_exceeded key={} limit={} windowSeconds={}", key, limit, window.getSeconds());
                throw new TooManyRequestsException(message);
            }

            bucket.addLast(now);
        }
    }
}
