package com.ctps.ctps_api.domain.auth.service;

import com.ctps.ctps_api.domain.auth.entity.User;
import java.time.LocalDateTime;

public interface PasswordResetNotifier {

    void send(User user, String resetLink, LocalDateTime expiresAt);
}
