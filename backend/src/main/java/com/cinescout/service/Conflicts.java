package com.cinescout.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

/** Turns the database's unique-constraint failures into {@link ConflictException}s the client can act on. */
public final class Conflicts {

    private static final Map<String, String> MESSAGES = Map.of(
            "uq_scenes_project_number", "A scene with that number already exists in this project",
            "uq_locations_scene_source", "That page is already saved for this scene",
            "uq_users_email_lower", "That email address is already registered");

    private Conflicts() {
    }

    /**
     * A {@link ConflictException} if the failure is one of our known unique constraints; otherwise
     * the original exception, since any other integrity failure is a bug, not a client error.
     */
    public static RuntimeException translate(DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                String message = MESSAGES.get(violation.getConstraintName());
                if (message != null) {
                    return new ConflictException(message);
                }
            }
        }
        return error;
    }
}
