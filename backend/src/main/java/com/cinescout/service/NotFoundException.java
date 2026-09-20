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
}
