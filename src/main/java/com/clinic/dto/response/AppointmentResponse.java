package com.clinic.dto.response;

import com.clinic.enums.AppointmentStatus;

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
        Instant updatedAt
) {
    public record PatientSummary(Long id, String fullName, String phone, String email) {
    }

    public record ServiceSummary(Long id, String name, Integer estimatedDuration, Long specialtyId, String specialtyName) {
    }

    public record DoctorSummary(Long id, String fullName, String slug) {
    }
}
