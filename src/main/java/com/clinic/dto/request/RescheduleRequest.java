package com.clinic.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record RescheduleRequest(
        @NotNull(message = "New date is required")
        LocalDate appointmentDate,

        @NotNull(message = "New time is required")
        LocalTime appointmentTime
) {
}
