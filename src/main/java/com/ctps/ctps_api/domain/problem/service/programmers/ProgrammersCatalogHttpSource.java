package com.ctps.ctps_api.domain.problem.service.programmers;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ProgrammersCatalogHttpSource implements ProgrammersCatalogSource {

    private final RestClient restClient = RestClient.create();
    private final String feedUrl;

    public ProgrammersCatalogHttpSource(@Value("${external.search.programmers.feed-url:}") String feedUrl) {
        this.feedUrl = feedUrl;
    }

    @Override
    public List<ProgrammersCatalogSourceItem> fetch() {
        if (!StringUtils.hasText(feedUrl)) {
            return List.of();
        }

        try {
            List<ProgrammersCatalogSourceItem> items = restClient.get()
                    .uri(feedUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return items == null ? List.of() : items;
        } catch (Exception exception) {
            log.warn("failed to fetch programmers catalog from http source", exception);
            return List.of();
        }
    }

    @Override
    public String sourceName() {
        return "http";
    }
}
