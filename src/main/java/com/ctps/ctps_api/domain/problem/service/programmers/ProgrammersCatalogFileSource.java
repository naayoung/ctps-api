package com.ctps.ctps_api.domain.problem.service.programmers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProgrammersCatalogFileSource implements ProgrammersCatalogSource {

    private final ObjectMapper objectMapper;

    @Value("${external.search.programmers.import-dir:}")
    private String importDir;

    @Override
    public List<ProgrammersCatalogSourceItem> fetch() {
        if (!StringUtils.hasText(importDir)) {
            return List.of();
        }

        try {
            Path latestFile;
            try (var paths = Files.list(Path.of(importDir))) {
                latestFile = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .filter(path -> !path.getFileName().toString().endsWith(".tmp"))
                        .max(Comparator.comparing(this::lastModifiedTime))
                        .orElse(null);
            }

            if (latestFile == null) {
                return List.of();
            }

            return objectMapper.readValue(latestFile.toFile(), new TypeReference<>() {
            });
        } catch (Exception exception) {
            log.warn("failed to fetch programmers catalog from file source", exception);
            return List.of();
        }
    }

    @Override
    public String sourceName() {
        return "file";
    }

    private long lastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception exception) {
            return Long.MIN_VALUE;
        }
    }
}
