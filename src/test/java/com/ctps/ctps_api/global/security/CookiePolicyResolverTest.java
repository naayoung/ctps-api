package com.ctps.ctps_api.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CookiePolicyResolverTest {

    @Test
    void localHttpEnvironmentUsesLaxCookies() {
        CookiePolicy policy = CookiePolicyResolver.resolve(
                "AUTO",
                "AUTO",
                "http://localhost:5173",
                "http://localhost:8080",
                "local"
        );

        assertThat(policy.secure()).isFalse();
        assertThat(policy.sameSite()).isEqualTo("Lax");
    }

    @Test
    void crossOriginHttpsFrontendDefaultsToSecureNoneEvenWithoutDeploymentFlag() {
        CookiePolicy policy = CookiePolicyResolver.resolve(
                "AUTO",
                "AUTO",
                "https://ctps-web.vercel.app",
                "",
                "local"
        );

        assertThat(policy.secure()).isTrue();
        assertThat(policy.sameSite()).isEqualTo("None");
    }

    @Test
    void sameOriginHttpsEnvironmentKeepsLaxWhileStillUsingSecureCookies() {
        CookiePolicy policy = CookiePolicyResolver.resolve(
                "AUTO",
                "AUTO",
                "https://ctps.example.com",
                "https://ctps.example.com",
                "production"
        );

        assertThat(policy.secure()).isTrue();
        assertThat(policy.sameSite()).isEqualTo("Lax");
    }

    @Test
    void explicitSameSiteNoneFallsBackToLaxWhenSecureIsDisabled() {
        CookiePolicy policy = CookiePolicyResolver.resolve(
                "false",
                "None",
                "https://ctps-web.vercel.app",
                "https://ctps-api.railway.app",
                "production"
        );

        assertThat(policy.secure()).isFalse();
        assertThat(policy.sameSite()).isEqualTo("Lax");
    }
}
