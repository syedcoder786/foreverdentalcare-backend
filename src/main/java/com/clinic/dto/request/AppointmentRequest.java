package com.clinic.dto.request;

import com.clinic.util.ValidationPatterns;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;

/** Public appointment request. Date-in-the-past and slot rules are checked in the service using the clinic timezone. */
public record AppointmentRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = ValidationPatterns.PHONE, message = "Please enter a valid phone number")
        String phone,

        @Email(message = "Please enter a valid email address")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @NotNull(message = "Please select a treatment")
        Long serviceId,

        /** Doctor to book with. May be omitted only when exactly one active doctor offers the service. */
        Long doctorId,

        @NotNull(message = "Preferred date is required")
        LocalDate appointmentDate,

        @NotNull(message = "Preferred time is required")
        LocalTime appointmentTime,

        @Size(max = 1000, message = "Message must be at most 1000 characters")
        String message,

        @AssertTrue(message = "Consent is required to process your appointment request")
        boolean consent
) {
}
