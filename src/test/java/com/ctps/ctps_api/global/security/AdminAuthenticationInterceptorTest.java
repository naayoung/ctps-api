package com.ctps.ctps_api.global.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ctps.ctps_api.global.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminAuthenticationInterceptorTest {

    @Test
    @DisplayName("허용된 CIDR 대역과 유효한 토큰이면 관리자 요청을 통과시킨다")
    void preHandle_allowsRequestWhenTokenAndIpAreValid() {
        AdminAuthenticationInterceptor interceptor = new AdminAuthenticationInterceptor(
                "secret-token",
                "10.0.0.0/8,127.0.0.1/32",
                false
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/external-search/metrics");
        request.addHeader("X-Admin-Token", "secret-token");
        request.setRemoteAddr("10.42.0.15");

        assertThatCode(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("허용되지 않은 IP면 관리자 요청을 차단한다")
    void preHandle_blocksRequestWhenIpIsNotAllowed() {
        AdminAuthenticationInterceptor interceptor = new AdminAuthenticationInterceptor(
                "secret-token",
                "10.0.0.0/8",
                false
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/external-search/metrics");
        request.addHeader("X-Admin-Token", "secret-token");
        request.setRemoteAddr("203.0.113.8");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("IP");
    }

    @Test
    @DisplayName("프록시 헤더 신뢰 설정이 켜지면 전달된 원본 IP를 기준으로 검사한다")
    void preHandle_usesForwardedHeaderWhenTrusted() {
        AdminAuthenticationInterceptor interceptor = new AdminAuthenticationInterceptor(
                "secret-token",
                "198.51.100.0/24",
                true
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/external-search/metrics");
        request.addHeader("X-Admin-Token", "secret-token");
        request.addHeader("X-Forwarded-For", "198.51.100.42, 10.0.0.5");
        request.setRemoteAddr("10.0.0.5");

        assertThatCode(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("토큰이 다르면 관리자 요청을 차단한다")
    void preHandle_blocksRequestWhenTokenIsInvalid() {
        AdminAuthenticationInterceptor interceptor = new AdminAuthenticationInterceptor(
                "secret-token",
                "127.0.0.1/32",
                false
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/external-search/metrics");
        request.addHeader("X-Admin-Token", "wrong-token");
        request.setRemoteAddr("127.0.0.1");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("인증");
    }
}
