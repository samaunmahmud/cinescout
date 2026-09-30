package com.cinescout.service;

/**
 * A request that is well formed but that a service cannot act on, for a reason tied to one of its fields.
 * Reported like a validation failure, naming the field to fix.
 */
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
