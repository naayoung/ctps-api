package com.ctps.ctps_api.domain.auth.service;

import com.ctps.ctps_api.domain.auth.entity.AuthProvider;
import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.auth.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthBootstrapService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${auth.bootstrap.username:ctps}")
    private String bootstrapUsername;

    @Value("${auth.bootstrap.password:ctps1234}")
    private String bootstrapPassword;

    @Value("${auth.bootstrap.display-name:CTPS 사용자}")
    private String bootstrapDisplayName;

    @Value("${auth.bootstrap.email:ctps@local.ctps}")
    private String bootstrapEmail;

    @Value("${auth.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;

    @Value("${app.deployment.mode:local}")
    private String deploymentMode;

    @Value("${RAILWAY_ENVIRONMENT:}")
    private String railwayEnvironment;

    @Value("${VERCEL_ENV:}")
    private String vercelEnvironment;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ensureBootstrapUser() {
        if (!bootstrapEnabled) {
            log.info("bootstrap user creation disabled");
            return;
        }

        if (!isLocalOrDevEnvironment()) {
            log.info("bootstrap user creation skipped for non-local environment mode={}", deploymentMode);
            return;
        }

        if (!StringUtils.hasText(bootstrapUsername) || !StringUtils.hasText(bootstrapPassword)) {
            log.warn("bootstrap user creation skipped because username/password were not explicitly configured");
            return;
        }

        userRepository.findByUsername(bootstrapUsername)
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(bootstrapUsername)
                        .passwordHash(passwordEncoder.encode(bootstrapPassword))
                        .displayName(bootstrapDisplayName)
                        .email(bootstrapEmail)
                        .primaryAuthProvider(AuthProvider.LOCAL)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    boolean isLocalOrDevEnvironment() {
        String normalized = deploymentMode == null ? "" : deploymentMode.trim().toLowerCase();

        if (StringUtils.hasText(railwayEnvironment) || StringUtils.hasText(vercelEnvironment)) {
            return false;
        }

        return "local".equals(normalized) || "dev".equals(normalized) || "development".equals(normalized);
    }
}
