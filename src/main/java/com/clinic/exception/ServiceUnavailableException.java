package com.clinic.exception;

/** Maps to HTTP 503, e.g. WhatsApp sign-in codes are not set up or WhatsApp could not deliver the message. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
