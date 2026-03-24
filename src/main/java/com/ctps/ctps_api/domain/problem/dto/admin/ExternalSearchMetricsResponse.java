package com.ctps.ctps_api.domain.problem.dto.admin;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExternalSearchMetricsResponse {

    private long cacheHits;
    private long cacheMisses;
    private Map<String, Long> providerSuccesses;
    private Map<String, Long> providerFailures;
}
