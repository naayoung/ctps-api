package com.ctps.ctps_api.global.config;

import com.ctps.ctps_api.global.security.AdminAuthenticationInterceptor;
import com.ctps.ctps_api.global.security.CsrfProtectionInterceptor;
import com.ctps.ctps_api.global.security.UserAuthenticationInterceptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthenticationInterceptor adminAuthenticationInterceptor;
    private final CsrfProtectionInterceptor csrfProtectionInterceptor;
    private final UserAuthenticationInterceptor userAuthenticationInterceptor;
    private final List<String> allowedOrigins;
    private final long corsMaxAgeSeconds;

    public WebMvcConfig(
            AdminAuthenticationInterceptor adminAuthenticationInterceptor,
            CsrfProtectionInterceptor csrfProtectionInterceptor,
            UserAuthenticationInterceptor userAuthenticationInterceptor,
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins,
            @Value("${app.cors.max-age-seconds:3600}") long corsMaxAgeSeconds
    ) {
        this.adminAuthenticationInterceptor = adminAuthenticationInterceptor;
        this.csrfProtectionInterceptor = csrfProtectionInterceptor;
        this.userAuthenticationInterceptor = userAuthenticationInterceptor;
        this.allowedOrigins = List.of(allowedOrigins.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        this.corsMaxAgeSeconds = corsMaxAgeSeconds;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userAuthenticationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/health");

        registry.addInterceptor(csrfProtectionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/health");

        registry.addInterceptor(adminAuthenticationInterceptor)
                .addPathPatterns("/api/admin/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(corsMaxAgeSeconds);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
