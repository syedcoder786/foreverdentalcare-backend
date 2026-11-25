package com.clinic.exception;

import com.clinic.enums.AppointmentStatus;

public class InvalidStatusTransitionException extends BusinessException {

    public InvalidStatusTransitionException(AppointmentStatus from, AppointmentStatus to) {
        super("status", "Cannot change appointment status from " + from + " to " + to);
    }
}
