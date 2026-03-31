package com.ctps.ctps_api.global.security;

import java.net.URI;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class CookiePolicyResolver {

    private final CookiePolicy cookiePolicy;
    private final String cookieDomain;

    public CookiePolicyResolver(
            @Value("${auth.session.secure-cookie:AUTO}") String secureCookie,
            @Value("${auth.session.same-site:AUTO}") String sameSite,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl,
            @Value("${app.backend.base-url:}") String backendBaseUrl,
            @Value("${app.deployment.mode:local}") String deploymentMode,
            @Value("${auth.session.cookie-domain:}") String cookieDomain
    ) {
        this.cookiePolicy = resolve(secureCookie, sameSite, frontendBaseUrl, backendBaseUrl, deploymentMode);
        this.cookieDomain = cookieDomain;

        log.info(
                "resolved cookie policy secure={} sameSite={} domain={} deploymentMode={} frontendBaseUrl={} backendBaseUrl={}",
                this.cookiePolicy.secure(),
                this.cookiePolicy.sameSite(),
                StringUtils.hasText(this.cookieDomain) ? this.cookieDomain : "(default)",
                deploymentMode,
                frontendBaseUrl,
                StringUtils.hasText(backendBaseUrl) ? backendBaseUrl : "(auto)"
        );
    }

    public CookiePolicy getPolicy() {
        return cookiePolicy;
    }

    public String getCookieDomain() {
        return cookieDomain;
    }

    static CookiePolicy resolve(
            String rawSecureCookie,
            String rawSameSite,
            String frontendBaseUrl,
            String backendBaseUrl,
            String deploymentMode
    ) {
        OriginDescriptor frontendOrigin = OriginDescriptor.from(frontendBaseUrl);
        OriginDescriptor backendOrigin = OriginDescriptor.from(backendBaseUrl);

        boolean secure = resolveSecureCookie(rawSecureCookie, frontendOrigin, backendOrigin, deploymentMode);
        String sameSite = resolveSameSite(rawSameSite, secure, frontendOrigin, backendOrigin, deploymentMode);
        return new CookiePolicy(secure, sameSite);
    }

    private static boolean resolveSecureCookie(
            String rawValue,
            OriginDescriptor frontendOrigin,
            OriginDescriptor backendOrigin,
            String deploymentMode
    ) {
        if (StringUtils.hasText(rawValue) && !"AUTO".equalsIgnoreCase(rawValue.trim())) {
            return Boolean.parseBoolean(rawValue.trim());
        }

        if (frontendOrigin.isHttps() || backendOrigin.isHttps()) {
            return true;
        }

        return isNonLocalMode(deploymentMode);
    }

    private static String resolveSameSite(
            String rawValue,
            boolean secure,
            OriginDescriptor frontendOrigin,
            OriginDescriptor backendOrigin,
            String deploymentMode
    ) {
        if (StringUtils.hasText(rawValue) && !"AUTO".equalsIgnoreCase(rawValue.trim())) {
            String normalized = capitalize(rawValue.trim());
            if ("None".equalsIgnoreCase(normalized) && !secure) {
                log.warn("sameSite=None requires secure cookies; falling back to Lax");
                return "Lax";
            }
            return normalized;
        }

        boolean crossOrigin = frontendOrigin.isConfigured()
                && (!backendOrigin.isConfigured() || !frontendOrigin.origin().equals(backendOrigin.origin()));

        if (crossOrigin && secure) {
            return "None";
        }

        if (isNonLocalMode(deploymentMode) && secure && frontendOrigin.isHttps() && !backendOrigin.isConfigured()) {
            return "None";
        }

        return "Lax";
    }

    private static boolean isNonLocalMode(String deploymentMode) {
        String normalizedMode = deploymentMode == null ? "" : deploymentMode.trim().toLowerCase(Locale.ROOT);
        return !(normalizedMode.isBlank()
                || "local".equals(normalizedMode)
                || "dev".equals(normalizedMode)
                || "development".equals(normalizedMode));
    }

    private static String capitalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "Lax";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private record OriginDescriptor(String origin, String scheme) {

        static OriginDescriptor from(String value) {
            if (!StringUtils.hasText(value)) {
                return new OriginDescriptor(null, null);
            }

            try {
                URI uri = URI.create(value.trim());
                String scheme = uri.getScheme();
                String host = uri.getHost();
                int port = uri.getPort();

                if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
                    return new OriginDescriptor(null, scheme);
                }

                return new OriginDescriptor(
                        scheme + "://" + host + (port > 0 ? ":" + port : ""),
                        scheme
                );
            } catch (Exception ignored) {
                return new OriginDescriptor(null, null);
            }
        }

        boolean isConfigured() {
            return StringUtils.hasText(origin);
        }

        boolean isHttps() {
            return "https".equalsIgnoreCase(scheme);
        }
    }
}
