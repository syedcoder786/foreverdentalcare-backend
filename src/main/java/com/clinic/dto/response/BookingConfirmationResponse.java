package com.clinic.dto.response;

import com.clinic.enums.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/** Returned to the public after submitting an appointment request. Contains no patient personal data. */
public record BookingConfirmationResponse(
        Long referenceId,
        AppointmentStatus status,
        String serviceName,
        String doctorName,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        LocalTime endTime
) {
}
