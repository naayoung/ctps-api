package com.ctps.ctps_api.global.security;

import com.ctps.ctps_api.global.exception.UnauthorizedException;
import java.util.Optional;

public final class CurrentUserContext {

    private static final ThreadLocal<AuthenticatedUser> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(AuthenticatedUser user) {
        HOLDER.set(user);
    }

    public static AuthenticatedUser getRequired() {
        AuthenticatedUser user = HOLDER.get();
        if (user == null) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }
        return user;
    }

    public static Optional<AuthenticatedUser> getOptional() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
