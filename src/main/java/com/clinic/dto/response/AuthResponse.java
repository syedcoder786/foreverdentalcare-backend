package com.clinic.dto.response;

/** Access token is returned in the body (kept in memory by the SPA); the refresh token travels only in an HttpOnly cookie. */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        AdminResponse admin
) {
}
