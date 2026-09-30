package com.cinescout.service;

import java.util.UUID;

/**
 * The resource does not exist <em>for this user</em>. Deliberately the same for "missing" and "someone
 * else's", so ids cannot be probed.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, UUID id) {
        super(resource + " " + id + " not found");
    }

    /** For a resource with no id to name, such as one looked up by a token. */
    public NotFoundException(String message) {
        super(message);
    }
}
