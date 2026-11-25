package com.clinic.exception;

/** The request conflicts with current state, e.g. the slot was already booked (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
