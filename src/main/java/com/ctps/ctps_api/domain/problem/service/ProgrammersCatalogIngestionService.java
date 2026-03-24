package com.ctps.ctps_api.domain.problem.service;

import com.ctps.ctps_api.domain.problem.entity.ProgrammersProblemCatalog;
import com.ctps.ctps_api.domain.problem.repository.ProgrammersProblemCatalogRepository;
import com.ctps.ctps_api.domain.problem.service.programmers.ProgrammersCatalogSource;
import com.ctps.ctps_api.domain.problem.service.programmers.ProgrammersCatalogSourceItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgrammersCatalogIngestionService {

    private final ProgrammersProblemCatalogRepository catalogRepository;
    private final ObjectMapper objectMapper;
    private final List<ProgrammersCatalogSource> sources;

    @Transactional
    public int ingestFromConfiguredFeed() {
        LocalDateTime now = LocalDateTime.now();
        int importedCount = 0;

        for (ProgrammersCatalogSource source : sources) {
            List<ProgrammersCatalogSourceItem> items = source.fetch();
            if (items.isEmpty()) {
                continue;
            }

            for (ProgrammersCatalogSourceItem item : items) {
                try {
                    String tagsJson = objectMapper.writeValueAsString(item.getTags() == null ? List.of() : item.getTags());
                    ProgrammersProblemCatalog catalog = catalogRepository.findByExternalId(item.getExternalId())
                            .orElseGet(() -> ProgrammersProblemCatalog.builder()
                                    .externalId(item.getExternalId())
                                    .title(item.getTitle())
                                    .problemNumber(item.getProblemNumber())
                                    .difficulty(item.getDifficulty())
                                    .tagsJson(tagsJson)
                                    .externalUrl(item.getExternalUrl())
                                    .recommendationReason(item.getRecommendationReason())
                                    .sourceUpdatedAt(now)
                                    .ingestedAt(now)
                                    .build());

                    catalog.refresh(
                            item.getTitle(),
                            item.getProblemNumber(),
                            item.getDifficulty(),
                            tagsJson,
                            item.getExternalUrl(),
                            item.getRecommendationReason(),
                            now,
                            now
                    );
                    catalogRepository.save(catalog);
                    importedCount++;
                } catch (Exception exception) {
                    log.warn("failed to ingest programmers catalog item from source={}", source.sourceName(), exception);
                }
            }

            log.info("programmers catalog ingest source={} importedCount={}", source.sourceName(), importedCount);
            return importedCount;
        }

        return 0;
    }

    @Scheduled(cron = "${external.search.programmers.ingest-cron:0 15 */6 * * *}")
    public void scheduledIngest() {
        int count = ingestFromConfiguredFeed();
        log.info("programmers catalog ingest finished count={}", count);
    }
}
