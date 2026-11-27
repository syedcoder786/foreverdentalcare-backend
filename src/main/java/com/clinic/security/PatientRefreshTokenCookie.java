package com.clinic.security;

import com.clinic.config.AppProperties;
import com.clinic.config.PatientAuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Patient session cookie: HttpOnly, Secure (configurable), SameSite like the admin cookie, scoped to /api/patient-auth. */
@Component
public class PatientRefreshTokenCookie {

    public static final String NAME = "clinic_patient_refresh";
    private static final String PATH = "/api/patient-auth";

    private final AppProperties properties;
    private final PatientAuthProperties patientProps;

    public PatientRefreshTokenCookie(AppProperties properties, PatientAuthProperties patientProps) {
        this.properties = properties;
        this.patientProps = patientProps;
    }

    public ResponseCookie create(String token) {
        return base(token).maxAge(Duration.ofDays(patientProps.refreshTokenTtlDays())).build();
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
