package com.ctps.ctps_api.domain.problem.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProgrammersCatalogIngestResponse {

    private int importedCount;
    private boolean success;
}
