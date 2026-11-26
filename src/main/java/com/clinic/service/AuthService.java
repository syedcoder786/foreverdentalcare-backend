package com.clinic.service;

import com.clinic.config.AppProperties;
import com.clinic.dto.request.LoginRequest;
import com.clinic.dto.response.AdminResponse;
import com.clinic.dto.response.AuthResponse;
import com.clinic.entity.Admin;
import com.clinic.entity.RefreshToken;
import com.clinic.exception.AuthenticationFailedException;
import com.clinic.repository.RefreshTokenRepository;
import com.clinic.security.AdminUserDetails;
import com.clinic.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Login, refresh-token rotation and logout.
 * <p>
 * Credentials are checked by Spring Security's {@link AuthenticationManager} (DaoAuthenticationProvider with
 * {@code AdminUserDetailsService} and BCrypt); access tokens are issued by {@link JwtService}.
 * <p>
 * Refresh tokens are 256-bit random values; only their SHA-256 hash is stored. Each refresh rotates the token
 * (the old one is revoked). Presenting an already-revoked token is treated as possible theft and revokes all of
 * that admin's sessions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AppProperties properties;

    public record AuthResult(AuthResponse response, String refreshToken) {
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email().trim(), request.password()));
        } catch (AuthenticationException ex) {
            log.warn("Failed admin login attempt: {}", ex.getClass().getSimpleName());
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        Admin admin = ((AdminUserDetails) authentication.getPrincipal()).admin();
        return issueTokens(admin);
    }

    /** noRollbackFor: a detected token-reuse must still persist the "revoke all sessions" action. */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthenticationFailedException("Session expired. Please log in again.");
        }
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new AuthenticationFailedException("Session expired. Please log in again."));
        if (stored.isRevoked()) {
            log.warn("Reuse of revoked refresh token detected for admin id {}; revoking all sessions", stored.getAdmin().getId());
            revokeAll(stored.getAdmin());
            throw new AuthenticationFailedException("Session expired. Please log in again.");
        }
        if (!stored.isUsable(Instant.now())) {
            throw new AuthenticationFailedException("Session expired. Please log in again.");
        }
        stored.setRevoked(true);
        return issueTokens(stored.getAdmin());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(t -> t.setRevoked(true));
    }

    /** Housekeeping: remove expired/revoked refresh tokens daily. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeStaleTokens() {
        int removed = refreshTokenRepository.deleteExpiredOrRevoked(Instant.now().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("Purged {} stale refresh tokens", removed);
        }
    }

    private AuthResult issueTokens(Admin admin) {
        String raw = generateRawToken();
        RefreshToken token = new RefreshToken();
        token.setAdmin(admin);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(properties.jwt().refreshTokenTtlDays())));
        refreshTokenRepository.save(token);

        AuthResponse response = new AuthResponse(
                jwtService.generateAccessToken(admin),
                "Bearer",
                jwtService.accessTokenTtlSeconds(),
                new AdminResponse(admin.getId(), admin.getName(), admin.getEmail(), admin.getRole()));
        return new AuthResult(response, raw);
    }

    private void revokeAll(Admin admin) {
        refreshTokenRepository.revokeAllForAdmin(admin.getId());
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
