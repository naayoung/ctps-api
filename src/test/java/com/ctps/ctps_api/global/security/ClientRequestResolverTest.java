package com.ctps.ctps_api.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientRequestResolverTest {

    @Test
    @DisplayName("기본 설정에서는 프록시 헤더를 신뢰하지 않고 원격 주소를 사용한다")
    void resolveClientKey_usesRemoteAddrWhenForwardedHeadersAreNotTrusted() {
        ClientRequestResolver resolver = new ClientRequestResolver(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.10");
        request.addHeader("X-Real-IP", "198.51.100.11");
        request.setRemoteAddr("10.0.0.5");

        assertThat(resolver.resolveClientKey(request)).isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("신뢰 설정이 켜진 경우에만 프록시 헤더를 사용한다")
    void resolveClientKey_usesForwardedHeaderWhenTrusted() {
        ClientRequestResolver resolver = new ClientRequestResolver(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.10, 10.0.0.5");
        request.setRemoteAddr("10.0.0.5");

        assertThat(resolver.resolveClientKey(request)).isEqualTo("198.51.100.10");
    }
}
