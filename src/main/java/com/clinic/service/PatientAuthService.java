package com.clinic.service;

import com.clinic.config.PatientAuthProperties;
import com.clinic.dto.PatientDtos.OtpVerifyResponse;
import com.clinic.dto.PatientDtos.PatientAuthResponse;
import com.clinic.dto.PatientDtos.PatientProfileResponse;
import com.clinic.dto.PatientDtos.SignupRequest;
import com.clinic.entity.Patient;
import com.clinic.entity.PatientRefreshToken;
import com.clinic.exception.AuthenticationFailedException;
import com.clinic.exception.BusinessException;
import com.clinic.repository.PatientRefreshTokenRepository;
import com.clinic.repository.PatientRepository;
import com.clinic.security.JwtService;
import com.clinic.util.PhoneNumbers;
import com.clinic.util.ValidationPatterns;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.Optional;

/**
 * Patient sign-in: a verified WhatsApp code signs a returning patient in, or lets a new number finish sign-up with a
 * name ("silent" sign-up: the mobile number is the account). Sessions work like the admin ones: a short-lived JWT
 * access token plus a rotating refresh token in an HttpOnly cookie, with reuse detection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** How long a just-replaced refresh token is still accepted (concurrent refreshes from tabs or retries). */

    private static final Duration ROTATION_GRACE = Duration.ofSeconds(60);
    private static final String SESSION_EXPIRED = "Your session has expired. Please sign in again.";

    private final OtpService otpService;
    private final PatientRepository patientRepository;
    private final PatientRefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PatientAuthProperties props;

    public record PatientAuthResult(PatientAuthResponse response, String refreshToken) {
    }

    public record VerifyResult(OtpVerifyResponse response, String refreshToken) {
    }

    /** noRollbackFor: a wrong code must still count as an attempt. */
    @Transactional(noRollbackFor = BusinessException.class)
    public VerifyResult verifyCode(String rawPhone, String code) {
        String phone = otpService.verify(rawPhone, code);
        Optional<Patient> existing = findByPhone(phone);
        if (existing.isPresent()) {
            PatientAuthResult auth = issueTokens(existing.get());
            return new VerifyResult(new OtpVerifyResponse(false, null, auth.response()), auth.refreshToken());
        }
        return new VerifyResult(new OtpVerifyResponse(true, jwtService.generateSignupToken(phone), null), null);
    }

    @Transactional
    public PatientAuthResult completeSignup(SignupRequest r) {
        String phone = jwtService.parseSignupToken(r.signupToken())
                .orElseThrow(() -> new BusinessException("Your verification has expired. Please request a new code."));
        Patient patient = findByPhone(phone).orElseGet(Patient::new);
        if (patient.getId() == null) {
            patient.setPhone(phone);
        }
        patient.setFullName(r.fullName().trim());
        String email = ValidationPatterns.trimToNull(r.email());
        if (email != null) {
            patient.setEmail(email.toLowerCase());
        }
        if (r.whatsappOptIn() != null) {
            patient.changeWhatsappOptIn(r.whatsappOptIn());
        }
        Patient saved = patientRepository.save(patient);
        log.info("Patient {} signed up with a verified mobile number", saved.getId());
        return issueTokens(saved);
    }

    /** noRollbackFor: a detected token reuse must still persist the "revoke all sessions" action. */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public PatientAuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthenticationFailedException(SESSION_EXPIRED);
        }
        PatientRefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new AuthenticationFailedException(SESSION_EXPIRED));
        Instant now = Instant.now();
        if (stored.isRevoked() && stored.getRotatedAt() != null
                && stored.getRotatedAt().isAfter(now.minus(ROTATION_GRACE)) && stored.getExpiresAt().isAfter(now)) {
            // Replaced a moment ago: a concurrent refresh (two tabs, a doubled request), not a stolen token.
            log.debug("Patient {} refreshed with a just-rotated token; issuing a fresh session", stored.getPatient().getId());
            return issueTokens(stored.getPatient());
        }
        if (stored.isRevoked()) {
            log.warn("Reuse of revoked patient refresh token detected for patient {}; revoking all sessions", stored.getPatient().getId());
            refreshTokenRepository.revokeAllForPatient(stored.getPatient().getId());
            throw new AuthenticationFailedException(SESSION_EXPIRED);
        }
        if (!stored.isUsable(now)) {
            throw new AuthenticationFailedException(SESSION_EXPIRED);
        }
        stored.setRevoked(true);
        stored.setRotatedAt(now);
        return issueTokens(stored.getPatient());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(t -> t.setRevoked(true));
    }

    @Scheduled(cron = "0 35 3 * * *")
    @Transactional
    public void purgeStaleTokens() {
        int removed = refreshTokenRepository.deleteExpiredOrRevoked(Instant.now().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("Purged {} stale patient sessions", removed);
        }
    }

    public static PatientProfileResponse toProfile(Patient p) {
        return new PatientProfileResponse(p.getId(), p.getFullName(), p.getPhone(), p.getEmail(), p.isWhatsappOptIn(), p.getCreatedAt());
    }

    /** Finds the patient for a verified number, including patients stored in older formats (without +91 etc.). */
    private Optional<Patient> findByPhone(String e164) {
        return patientRepository.findByPhoneIn(PhoneNumbers.storedVariants(e164, otpService.defaultCountryCode()))
                .stream()
                .sorted((a, b) -> Boolean.compare(!e164.equals(a.getPhone()), !e164.equals(b.getPhone())))
                .findFirst();
    }

    private PatientAuthResult issueTokens(Patient patient) {
        String raw = generateRawToken();
        PatientRefreshToken token = new PatientRefreshToken();
        token.setPatient(patient);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(props.refreshTokenTtlDays())));
        refreshTokenRepository.save(token);
        PatientAuthResponse response = new PatientAuthResponse(
                jwtService.generatePatientAccessToken(patient), "Bearer", jwtService.accessTokenTtlSeconds(), toProfile(patient));
        return new PatientAuthResult(response, raw);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
