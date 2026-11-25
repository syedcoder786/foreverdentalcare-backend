package com.clinic.exception;

public class SlotUnavailableException extends ConflictException {

    public SlotUnavailableException() {
        super("This time slot has just been booked. Please choose another time.");
    }
}
