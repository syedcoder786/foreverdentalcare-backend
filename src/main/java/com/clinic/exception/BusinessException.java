package com.clinic.exception;

import lombok.Getter;

/** A request that is well-formed but violates a business rule (HTTP 422). Optionally tied to a request field. */
@Getter
public class BusinessException extends RuntimeException {

    private final String field;

    public BusinessException(String message) {
        this(null, message);
    }

    public BusinessException(String field, String message) {
        super(message);
        this.field = field;
    }
}
