package com.ctps.ctps_api.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class OriginVerifierTest {

    @Test
    void allowsConfiguredOriginPatternFromOriginHeader() {
        OriginVerifier verifier = new OriginVerifier(
                new CorsOriginProperties(
                        "https://ctps-web.vercel.app",
                        "https://*.vercel.app"
                )
        );
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader("Origin", "https://preview-123.vercel.app");

        assertThat(verifier.isAllowed(request)).isTrue();
    }

    @Test
    void allowsConfiguredOriginPatternFromRefererHeader() {
        OriginVerifier verifier = new OriginVerifier(
                new CorsOriginProperties(
                        "https://ctps-web.vercel.app",
                        "https://*.vercel.app"
                )
        );
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader("Referer", "https://preview-123.vercel.app/search");

        assertThat(verifier.isAllowed(request)).isTrue();
    }
}
