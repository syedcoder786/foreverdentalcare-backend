package com.clinic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Patient sign-in (WhatsApp one-time code) settings, bound from {@code app.patient-auth.*}. Every value has a default,
 * so nothing has to be configured. The WhatsApp credentials themselves are edited in Admin -> Settings.
 *
 * @param otpTtlMinutes        how long a code stays valid
 * @param otpMaxAttempts       wrong guesses allowed per code
 * @param otpResendSeconds     minimum wait before requesting another code for the same number
 * @param otpMaxPerHour        codes per phone number per hour
 * @param logOtpCodes          DEVELOPMENT ONLY: when WhatsApp is not set up, write codes to the server log instead
 * @param refreshTokenTtlDays  how long a patient stays signed in without activity
 * @param whatsappApiBaseUrl   Graph API base URL
 * @param whatsappApiVersion   Graph API version, e.g. v25.0
 */
@ConfigurationProperties(prefix = "app.patient-auth")
public record PatientAuthProperties(
        @DefaultValue("5") int otpTtlMinutes,
        @DefaultValue("5") int otpMaxAttempts,
        @DefaultValue("30") int otpResendSeconds,
        @DefaultValue("5") int otpMaxPerHour,
        @DefaultValue("false") boolean logOtpCodes,
        @DefaultValue("30") int refreshTokenTtlDays,
        @DefaultValue("https://graph.facebook.com") String whatsappApiBaseUrl,
        @DefaultValue("v25.0") String whatsappApiVersion
) {
}
