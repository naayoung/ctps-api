package com.ctps.ctps_api.domain.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardTagStatResponse {

    private String tag;
    private long count;
}
