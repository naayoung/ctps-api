package com.ctps.ctps_api.domain.auth.service;

import com.ctps.ctps_api.domain.auth.entity.User;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    @Override
    public void send(User user, String resetLink, LocalDateTime expiresAt) {
        log.info(
                "password_reset_requested userId={} email={} expiresAt={} resetLink={}",
                user.getId(),
                user.getEmail(),
                expiresAt,
                resetLink
        );
    }
}
