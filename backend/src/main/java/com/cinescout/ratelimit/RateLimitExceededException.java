package com.cinescout.ratelimit;

import java.time.Duration;

/** A caller has used up a {@link RateLimit} for now. Reported as 429 with a Retry-After. */
public class RateLimitExceededException extends RuntimeException {

    private final RateLimit limit;
    private final Duration retryAfter;

    public RateLimitExceededException(RateLimit limit, Duration retryAfter) {
        super(limit + " rate limit reached; next call allowed in " + retryAfter);
        this.limit = limit;
        this.retryAfter = retryAfter;
    }

    public RateLimit limit() {
        return limit;
    }

    /** How long until one more call is allowed. */
    public Duration retryAfter() {
        return retryAfter;
    }

    /** {@link #retryAfter()} rounded up to whole seconds, for a Retry-After header. */
    public long retryAfterSeconds() {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }

    /** What to tell the user: which allowance is used up and when to come back (never too early). */
    public String detail() {
        String what = switch (limit) {
            case AI -> "You have reached the limit on AI requests for now";
            case SCOUTING -> "You have reached the limit on scouting runs for now";
            case LOOKUPS -> "You have reached the limit on map and video lookups for now";
            case LOGIN -> "Too many failed logins from your network";
            case REGISTER -> "Too many accounts have been opened from your network";
        };
        return what + "; try again in " + inWords(retryAfterSeconds()) + ".";
    }

    private static String inWords(long seconds) {
        if (seconds < 60) {
            return seconds == 1 ? "1 second" : seconds + " seconds";
        }
        long minutes = (seconds + 59) / 60;
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
