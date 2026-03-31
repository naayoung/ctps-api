package com.ctps.ctps_api.global.security;

public record CookiePolicy(boolean secure, String sameSite) {
}
