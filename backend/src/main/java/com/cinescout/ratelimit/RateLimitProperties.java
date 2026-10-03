package com.cinescout.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Objects;

/**
 * How often each {@link RateLimit} may be used. A rule allows a burst of {@code capacity} calls and then refills
 * evenly over {@code period}: 30 per hour means one more call every two minutes once the 30 are spent. A rule set
 * in configuration needs both values.
 *
 * @param enabled  off turns every limit off (the tests do; a trusted single-user install may)
 */
@ConfigurationProperties("cinescout.rate-limits")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        Rule ai,
        Rule scouting,
        Rule lookups,
        Rule login,
        Rule register,
        Rule guest
) {

    public RateLimitProperties {
        ai = Objects.requireNonNullElse(ai, new Rule(30, Duration.ofHours(1)));
        scouting = Objects.requireNonNullElse(scouting, new Rule(10, Duration.ofHours(1)));
        lookups = Objects.requireNonNullElse(lookups, new Rule(120, Duration.ofHours(1)));
        login = Objects.requireNonNullElse(login, new Rule(20, Duration.ofMinutes(10)));
        register = Objects.requireNonNullElse(register, new Rule(5, Duration.ofHours(1)));
        guest = Objects.requireNonNullElse(guest, new Rule(60, Duration.ofHours(1)));
    }

    public Rule rule(RateLimit limit) {
        return switch (limit) {
            case AI -> ai;
            case SCOUTING -> scouting;
            case LOOKUPS -> lookups;
            case LOGIN -> login;
            case REGISTER -> register;
            case GUEST -> guest;
        };
    }

    public record Rule(int capacity, Duration period) {

        public Rule {
            if (capacity < 1) {
                throw new IllegalArgumentException("A rate limit's capacity must be at least 1, was " + capacity);
            }
            if (period == null || period.isNegative() || period.isZero()) {
                throw new IllegalArgumentException("A rate limit needs a positive period, was " + period);
            }
        }
    }
}
