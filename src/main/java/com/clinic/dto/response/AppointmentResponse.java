package com.clinic.dto.response;

import com.clinic.enums.AppointmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/** Full appointment view for the admin dashboard. */
public record AppointmentResponse(
        Long id,
        PatientSummary patient,
        ServiceSummary service,
        DoctorSummary doctor,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        LocalTime endTime,
        AppointmentStatus status,
        String message,
        Set<AppointmentStatus> allowedTransitions,
        boolean reschedulable,
        Instant createdAt,
        Instant updatedAt,
        /** Doctor's consultation fee at booking time; null = discussed at consultation. */
        BigDecimal consultationFee,
        /** Set when this appointment carries the online first-visit discount (on the consultation fee). */
        Integer firstVisitDiscountPercent,
        /** Treatment's own fee at booking time. */
        BigDecimal treatmentFee,
        /** Treatment offer percent covering this appointment's date. */
        Integer treatmentDiscountPercent,
        /** Total after both discounts. */
        BigDecimal finalFee,
        boolean onlineBooking
) {
    public record PatientSummary(Long id, String fullName, String phone, String email) {
    }

    public record ServiceSummary(Long id, String name, Integer estimatedDuration, Long specialtyId, String specialtyName) {
    }

    public record DoctorSummary(Long id, String fullName, String slug) {
    }
}
