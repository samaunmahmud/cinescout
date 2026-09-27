package com.cinescout.security;

import com.cinescout.ratelimit.RateLimitExceededException;
import org.springframework.security.core.AuthenticationException;

/**
 * A password check refused because the client has failed too often lately. An {@link AuthenticationException} so
 * Spring Security routes it to the entry point, which answers 429 instead of 401.
 */
public class LoginThrottledException extends AuthenticationException {

    private final RateLimitExceededException limit;

    LoginThrottledException(RateLimitExceededException limit) {
        super(limit.getMessage(), limit);
        this.limit = limit;
    }

    public RateLimitExceededException limit() {
        return limit;
    }
}
