package com.clinic.dto.request;

import com.clinic.enums.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @NotNull(message = "Status is required")
        AppointmentStatus status
) {
}
