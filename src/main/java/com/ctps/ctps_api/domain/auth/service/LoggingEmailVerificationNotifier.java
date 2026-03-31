package com.ctps.ctps_api.domain.auth.service;

import com.ctps.ctps_api.domain.auth.entity.User;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingEmailVerificationNotifier implements EmailVerificationNotifier {

    @Override
    public void send(User user, String verificationLink, LocalDateTime expiresAt) {
        log.info(
                "email_verification_requested userId={} email={} expiresAt={} verificationLink=[REDACTED]",
                user.getId(),
                user.getEmail(),
                expiresAt
        );
    }
}
