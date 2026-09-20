package com.cinescout.service;

/** The request is valid but clashes with existing data (a duplicate). The message is safe to show to the client. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
