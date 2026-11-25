package com.clinic.security;

import com.clinic.config.AppProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the refresh-token cookie: HttpOnly (not readable by JavaScript), Secure (HTTPS only, configurable for local
 * http dev), SameSite=Strict (not sent on cross-site requests) and scoped to /api/auth.
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "clinic_refresh";
    private static final String PATH = "/api/auth";

    private final AppProperties properties;

    public RefreshTokenCookie(AppProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String token) {
        return base(token).maxAge(Duration.ofDays(properties.jwt().refreshTokenTtlDays())).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite(properties.cookie().sameSite())
                .path(PATH);
    }
}
