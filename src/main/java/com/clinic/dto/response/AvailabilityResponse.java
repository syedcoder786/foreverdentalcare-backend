package com.clinic.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AvailabilityResponse(
        LocalDate date,
        boolean open,
        String reason,
        List<Slot> slots
) {
    public record Slot(LocalTime time, boolean available) {
    }
}
