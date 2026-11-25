package com.clinic.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Appointment lifecycle.
 * <pre>
 * PENDING   -> CONFIRMED | CANCELLED
 * CONFIRMED -> COMPLETED | CANCELLED
 * COMPLETED, CANCELLED are terminal.
 * </pre>
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED;

    public Set<AppointmentStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(COMPLETED, CANCELLED);
            case CANCELLED, COMPLETED -> EnumSet.noneOf(AppointmentStatus.class);
        };
    }

    public boolean canTransitionTo(AppointmentStatus target) {
        return allowedTransitions().contains(target);
    }

    /** Whether an appointment in this status occupies its time slot. */
    public boolean occupiesSlot() {
        return this != CANCELLED;
    }

    public boolean isReschedulable() {
        return this == PENDING || this == CONFIRMED;
    }
}
