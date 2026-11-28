package com.clinic.dto;

import com.clinic.enums.AppointmentStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Requests and responses for patient sign-in (WhatsApp code) and the patient profile. */
public final class PatientDtos {

    private PatientDtos() {
    }

    // ---------- sign-in ----------

    public record OtpRequest(
            @NotBlank(message = "Please enter your mobile number")
            @Size(max = 25)
            String phone
    ) {
    }

    public record OtpSentResponse(String phone, String maskedPhone, long expiresInSeconds, long resendAfterSeconds) {
    }

    public record OtpVerifyRequest(
            @NotBlank(message = "Please enter your mobile number") @Size(max = 25) String phone,
            @NotBlank(message = "Please enter the 6-digit code") @Pattern(regexp = "^\\s*\\d{6}\\s*$", message = "The code has 6 digits") String code
    ) {
    }

    /**
     * Result of a correct code. Returning patients get {@code auth} straight away; new numbers get a short-lived
     * {@code signupToken} to send with their name to /signup.
     */
    public record OtpVerifyResponse(boolean newPatient, String signupToken, PatientAuthResponse auth) {
    }

    public record SignupRequest(
            @NotBlank String signupToken,
            @NotBlank(message = "Please enter your name")
            @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
            String fullName,
            @Email(message = "Please enter a valid email address") @Size(max = 150) String email,
            /** Agreed to receive offers and updates on WhatsApp. */
            Boolean whatsappOptIn
    ) {
    }

    public record PatientAuthResponse(String accessToken, String tokenType, long expiresIn, PatientProfileResponse patient) {
    }

    // ---------- profile ----------

    public record PatientProfileResponse(Long id, String fullName, String phone, String email, boolean whatsappOptIn, Instant createdAt) {
    }

    public record PatientProfileRequest(
            @NotBlank(message = "Please enter your name")
            @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
            String fullName,
            @Email(message = "Please enter a valid email address") @Size(max = 150) String email,
            /** null keeps the current choice. */
            Boolean whatsappOptIn
    ) {
    }

    // ---------- appointments ----------

    /** Booking by a signed-in patient: name and phone come from the profile. */
    public record PatientBookingRequest(
            @NotNull(message = "Please select a treatment") Long serviceId,
            Long doctorId,
            @NotNull(message = "Preferred date is required") LocalDate appointmentDate,
            @NotNull(message = "Preferred time is required") LocalTime appointmentTime,
            @Size(max = 1000, message = "Message must be at most 1000 characters") String message,
            @AssertTrue(message = "Consent is required to process your appointment request") boolean consent
    ) {
    }

    public record PatientAppointmentResponse(
            Long id,
            AppointmentStatus status,
            String serviceName,
            String doctorName,
            String doctorSlug,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            LocalTime endTime,
            String message,
            Instant createdAt,
            BigDecimal consultationFee,
            /** Set when this appointment carries the online first-visit discount. */
            Integer firstVisitDiscountPercent,
            BigDecimal treatmentFee,
            Integer treatmentDiscountPercent,
            BigDecimal finalFee
    ) {
    }
}
