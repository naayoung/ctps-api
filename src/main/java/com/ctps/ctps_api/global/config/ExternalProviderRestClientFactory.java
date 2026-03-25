package com.ctps.ctps_api.global.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExternalProviderRestClientFactory {

    private final Duration connectTimeout;
    private final Duration readTimeout;

    public ExternalProviderRestClientFactory(
            @Value("${external.search.http.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${external.search.http.read-timeout-ms:5000}") long readTimeoutMs
    ) {
        this.connectTimeout = Duration.ofMillis(connectTimeoutMs);
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
    }

    public RestClient create(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
        requestFactory.setReadTimeout((int) readTimeout.toMillis());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
