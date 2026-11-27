package com.clinic.service;

import com.clinic.config.AppProperties;
import com.clinic.config.PatientAuthProperties;
import com.clinic.entity.OtpChallenge;
import com.clinic.entity.WhatsAppSettings;
import com.clinic.exception.BusinessException;
import com.clinic.exception.ServiceUnavailableException;
import com.clinic.exception.TooManyRequestsException;
import com.clinic.repository.OtpChallengeRepository;
import com.clinic.util.PhoneNumbers;
import com.clinic.whatsapp.WhatsAppClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * One-time sign-in codes sent over WhatsApp.
 * <ul>
 *   <li>6 digits, valid for {@code otpTtlMinutes}; requesting a new code invalidates the previous one.</li>
 *   <li>At most {@code otpMaxAttempts} wrong guesses per code, {@code otpResendSeconds} between requests and
 *       {@code otpMaxPerHour} codes per number per hour (plus per-IP limits in the rate-limit filter).</li>
 *   <li>Only an HMAC-SHA256 of phone + code is stored.</li>
 * </ul>
 */
@Slf4j
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository repository;
    private final WhatsAppSettingsService whatsAppSettings;
    private final WhatsAppClient whatsAppClient;
    private final PatientAuthProperties props;
    private final SecretKeySpec hmacKey;

    public OtpService(OtpChallengeRepository repository, WhatsAppSettingsService whatsAppSettings,
                      WhatsAppClient whatsAppClient, PatientAuthProperties props, AppProperties appProperties) {
        this.repository = repository;
        this.whatsAppSettings = whatsAppSettings;
        this.whatsAppClient = whatsAppClient;
        this.props = props;
        this.hmacKey = new SecretKeySpec(("otp:" + appProperties.jwt().secret()).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public record OtpSent(String phone, String maskedPhone, long expiresInSeconds, long resendAfterSeconds) {
    }

    /** Normalises a number with the clinic's default country code. */
    @Transactional(readOnly = true)
    public String normalize(String rawPhone) {
        return PhoneNumbers.toE164(rawPhone, whatsAppSettings.load().getDefaultCountryCode());
    }

    @Transactional(readOnly = true)
    public String defaultCountryCode() {
        return whatsAppSettings.load().getDefaultCountryCode();
    }

    @Transactional
    public OtpSent send(String rawPhone) {
        WhatsAppSettings settings = whatsAppSettings.load();
        String phone = PhoneNumbers.toE164(rawPhone, settings.getDefaultCountryCode());
        Instant now = Instant.now();

        repository.findFirstByPhoneOrderByCreatedAtDesc(phone).ifPresent(last -> {
            long wait = Duration.between(now, last.getCreatedAt().plusSeconds(props.otpResendSeconds())).toSeconds();
            if (wait > 0) {
                throw new TooManyRequestsException("Please wait " + wait + " seconds before requesting a new code.", wait);
            }
        });
        if (repository.countByPhoneAndCreatedAtAfter(phone, now.minus(Duration.ofHours(1))) >= props.otpMaxPerHour()) {
            throw new TooManyRequestsException("Too many codes requested for this number. Please try again in an hour.", 3600);
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        repository.consumeAllForPhone(phone);
        OtpChallenge challenge = new OtpChallenge();
        challenge.setPhone(phone);
        challenge.setCodeHash(hash(phone, code));
        challenge.setExpiresAt(now.plus(Duration.ofMinutes(props.otpTtlMinutes())));
        repository.save(challenge);

        deliver(settings, phone, code);
        return new OtpSent(phone, PhoneNumbers.mask(phone), Duration.ofMinutes(props.otpTtlMinutes()).toSeconds(), props.otpResendSeconds());
    }

    /**
     * Checks a code. Returns the verified E.164 number; throws {@link BusinessException} (field "code") otherwise.
     * noRollbackFor keeps the failed-attempt counter when a wrong code is entered.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public String verify(String rawPhone, String code) {
        String phone = normalize(rawPhone);
        String expired = "This code has expired. Please request a new one.";
        OtpChallenge challenge = repository.findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> new BusinessException("code", expired));
        if (!challenge.getExpiresAt().isAfter(Instant.now())) {
            challenge.setConsumed(true);
            throw new BusinessException("code", expired);
        }
        if (challenge.getAttempts() >= props.otpMaxAttempts()) {
            challenge.setConsumed(true);
            throw new BusinessException("code", "Too many wrong attempts. Please request a new code.");
        }
        challenge.setAttempts(challenge.getAttempts() + 1);
        String candidate = code == null ? "" : code.replaceAll("\\D", "");
        boolean matches = MessageDigest.isEqual(
                hash(phone, candidate).getBytes(StandardCharsets.UTF_8),
                challenge.getCodeHash().getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            int left = props.otpMaxAttempts() - challenge.getAttempts();
            if (left <= 0) {
                challenge.setConsumed(true);
                throw new BusinessException("code", "Too many wrong attempts. Please request a new code.");
            }
            throw new BusinessException("code", "That code is not correct. " + left + (left == 1 ? " attempt" : " attempts") + " left.");
        }
        challenge.setConsumed(true);
        return phone;
    }

    @Scheduled(cron = "0 40 3 * * *")
    @Transactional
    public void purgeOldChallenges() {
        int removed = repository.deleteCreatedBefore(Instant.now().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("Purged {} old sign-in codes", removed);
        }
    }

    private void deliver(WhatsAppSettings settings, String phone, String code) {
        if (settings.isReady()) {
            try {
                whatsAppClient.sendOtp(settings, phone, code, true);
                return;
            } catch (WhatsAppClient.WhatsAppException ex) {
                log.warn("Sign-in code for {} not sent: {}", PhoneNumbers.mask(phone), ex.getMessage());
                throw new ServiceUnavailableException(
                        "We couldn't send the code on WhatsApp. Please check that this number uses WhatsApp, or call the clinic.");
            }
        }
        if (props.logOtpCodes()) {
            log.warn("DEV ONLY - WhatsApp sign-in is not set up. Sign-in code for {} is {}", PhoneNumbers.mask(phone), code);
            return;
        }
        throw new ServiceUnavailableException("Mobile sign-in is not available right now. Please call the clinic to book.");
    }

    private String hash(String phone, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal((phone + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}
