package com.ctps.ctps_api.domain.problem.service.programmers;

import java.util.List;

public interface ProgrammersCatalogSource {

    List<ProgrammersCatalogSourceItem> fetch();

    String sourceName();
}
