package com.cinescout.ratelimit;

import com.cinescout.ratelimit.RateLimitProperties.Rule;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    /** A clock the test moves by hand. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-09-27T10:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private final TestClock clock = new TestClock();

    /** AI: 3 an hour, so one more every 20 minutes. */
    private RateLimiter limiter(boolean enabled) {
        return new RateLimiter(new RateLimitProperties(enabled, new Rule(3, Duration.ofHours(1)), null, null, null, null, null), clock);
    }

    @Test
    void allowsABurstUpToTheCapacityThenSaysHowLongToWait() {
        RateLimiter limits = limiter(true);

        for (int i = 0; i < 3; i++) {
            assertThat(limits.tryAcquire(RateLimit.AI, "ada")).isZero();
        }
        assertThat(limits.tryAcquire(RateLimit.AI, "ada")).isEqualTo(Duration.ofMinutes(20));

        clock.advance(Duration.ofMinutes(5));
        assertThat(limits.tryAcquire(RateLimit.AI, "ada")).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void refillsEvenlyAndNeverBeyondTheCapacity() {
        RateLimiter limits = limiter(true);
        for (int i = 0; i < 3; i++) {
            limits.tryAcquire(RateLimit.AI, "ada");
        }

        clock.advance(Duration.ofMinutes(20));
        assertThat(limits.tryAcquire(RateLimit.AI, "ada")).isZero();
        assertThat(limits.tryAcquire(RateLimit.AI, "ada")).isPositive();

        clock.advance(Duration.ofDays(2));
        for (int i = 0; i < 3; i++) {
            assertThat(limits.tryAcquire(RateLimit.AI, "ada")).isZero();
        }
        assertThat(limits.tryAcquire(RateLimit.AI, "ada")).isPositive();
    }

    @Test
    void eachCallerAndEachLimitHasItsOwnAllowance() {
        RateLimiter limits = limiter(true);
        for (int i = 0; i < 3; i++) {
            limits.tryAcquire(RateLimit.AI, "ada");
        }

        assertThat(limits.tryAcquire(RateLimit.AI, "ada")).isPositive();
        assertThat(limits.tryAcquire(RateLimit.AI, "grace")).isZero();
        assertThat(limits.tryAcquire(RateLimit.SCOUTING, "ada")).isZero();
    }

    @Test
    void aRefundGivesOneCallBackButNeverMoreThanTheCapacity() {
        RateLimiter limits = limiter(true);
        for (int i = 0; i < 3; i++) {
            limits.tryAcquire(RateLimit.AI, "ada");
        }

        limits.refund(RateLimit.AI, "ada");
        assertThat(limits.tryAcquire(RateLimit.AI, "ada")).isZero();
        assertThat(limits.tryAcquire(RateLimit.AI, "ada")).isPositive();

        RateLimiter fresh = limiter(true);
        fresh.tryAcquire(RateLimit.AI, "grace");
        fresh.refund(RateLimit.AI, "grace");
        fresh.refund(RateLimit.AI, "grace");
        for (int i = 0; i < 3; i++) {
            assertThat(fresh.tryAcquire(RateLimit.AI, "grace")).isZero();
        }
        assertThat(fresh.tryAcquire(RateLimit.AI, "grace")).isPositive();
    }

    @Test
    void whenDisabledEverythingIsAllowed() {
        RateLimiter limits = limiter(false);

        for (int i = 0; i < 100; i++) {
            assertThat(limits.tryAcquire(RateLimit.AI, "ada")).isZero();
        }
    }

    @Test
    void acquireFailsWithTheLimitAndTheWait() {
        RateLimiter limits = limiter(true);
        for (int i = 0; i < 3; i++) {
            limits.acquire(RateLimit.AI, "ada").block();
        }

        assertThatThrownBy(() -> limits.acquire(RateLimit.AI, "ada").block())
                .isInstanceOfSatisfying(RateLimitExceededException.class, limited -> {
                    assertThat(limited.limit()).isEqualTo(RateLimit.AI);
                    assertThat(limited.retryAfterSeconds()).isEqualTo(1200);
                    assertThat(limited.detail()).isEqualTo("You have reached the limit on AI requests for now; try again in 20 minutes.");
                });
    }

    @Test
    void theWaitIsRoundedUpInWords() {
        assertThat(new RateLimitExceededException(RateLimit.LOGIN, Duration.ofMillis(1)).detail()).endsWith("in 1 second.");
        assertThat(new RateLimitExceededException(RateLimit.LOGIN, Duration.ofMillis(40_001)).detail()).endsWith("in 41 seconds.");
        assertThat(new RateLimitExceededException(RateLimit.LOGIN, Duration.ofSeconds(60)).detail()).endsWith("in 1 minute.");
        assertThat(new RateLimitExceededException(RateLimit.LOGIN, Duration.ofSeconds(61)).detail()).endsWith("in 2 minutes.");
    }

    @Test
    void rulesMustBePositiveAndUnsetOnesGetDefaults() {
        assertThatThrownBy(() -> new Rule(0, Duration.ofHours(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Rule(5, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Rule(5, null)).isInstanceOf(IllegalArgumentException.class);

        RateLimitProperties defaults = new RateLimitProperties(true, null, null, null, null, null, null);
        for (RateLimit limit : RateLimit.values()) {
            assertThat(defaults.rule(limit)).isNotNull();
        }
        assertThat(defaults.scouting()).isEqualTo(new Rule(10, Duration.ofHours(1)));
    }
}
