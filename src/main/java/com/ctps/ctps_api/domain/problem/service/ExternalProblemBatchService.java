package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import java.lang.reflect.Field;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalProblemBatchService {

    private final ExternalProblemSearchService externalProblemSearchService;

    @Value("${external.search.batch.seed-keywords:그래프,DP,브루트포스,BFS,DFS}")
    private String seedKeywords;

    @Scheduled(cron = "${external.search.batch.cron:0 0 */6 * * *}")
    public void refreshSeedQueries() {
        for (String keyword : seedKeywords.split(",")) {
            String trimmed = keyword.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            try {
                ProblemSearchRequest request = new ProblemSearchRequest();
                setField(request, "keyword", trimmed);
                setField(request, "size", 12);
                externalProblemSearchService.search(request);
            } catch (Exception exception) {
                log.warn("failed to refresh external seed query: {}", trimmed, exception);
            }
        }
    }

    private void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
