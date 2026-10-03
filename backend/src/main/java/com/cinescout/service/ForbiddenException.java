package com.cinescout.service;

/** The user is on the project but their role does not allow this; answered 403 with the message. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
