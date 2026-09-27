package com.cinescout.ratelimit;

import com.cinescout.ratelimit.RateLimitProperties.Rule;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token buckets per {@link RateLimit} and caller (a user id or a client address), kept in memory. That fits the
 * single-instance deployment: a restart forgets them, and several instances would each allow the full rate.
 * Callers that have gone quiet (their bucket full again) are dropped now and then, so memory stays bounded by the
 * callers active within one period.
 */
public class RateLimiter {

    private static final int SWEEP_EVERY = 1024;

    private final RateLimitProperties props;
    private final Clock clock;
    private final Map<RateLimit, Map<String, Bucket>> buckets = new EnumMap<>(RateLimit.class);
    private final AtomicLong calls = new AtomicLong();

    public RateLimiter(RateLimitProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        for (RateLimit limit : RateLimit.values()) {
            buckets.put(limit, new ConcurrentHashMap<>());
        }
    }

    /**
     * Takes one call from {@code caller}'s allowance, or fails with {@link RateLimitExceededException} when there is
     * none left. Checked when subscribed to, so it can lead a reactive chain.
     */
    public Mono<Void> acquire(RateLimit limit, Object caller) {
        return Mono.defer(() -> {
            Duration wait = tryAcquire(limit, String.valueOf(caller));
            return wait.isZero() ? Mono.empty() : Mono.error(new RateLimitExceededException(limit, wait));
        });
    }

    /**
     * Gives back a call taken by {@link #acquire}, for limits that count only failures: take one before trying (so
     * a burst of parallel attempts cannot all slip through), give it back when the attempt succeeds.
     */
    public void refund(RateLimit limit, Object caller) {
        if (!props.enabled()) {
            return;
        }
        Rule rule = props.rule(limit);
        Instant now = clock.instant();
        buckets.get(limit).computeIfPresent(String.valueOf(caller), (key, bucket) -> bucket.refilled(rule, now).giveBack(rule));
    }

    /** Zero when the call is allowed (and counted), otherwise how long until it would be. */
    Duration tryAcquire(RateLimit limit, String caller) {
        if (!props.enabled()) {
            return Duration.ZERO;
        }
        Rule rule = props.rule(limit);
        Instant now = clock.instant();
        Map<String, Bucket> callers = buckets.get(limit);
        if (calls.incrementAndGet() % SWEEP_EVERY == 0) {
            callers.values().removeIf(bucket -> bucket.refilled(rule, now).tokens() >= rule.capacity());
        }
        Bucket after = callers.compute(caller, (key, bucket) -> {
            Bucket current = bucket == null ? Bucket.full(rule, now) : bucket.refilled(rule, now);
            return current.tokens() >= 1 ? current.take() : current;
        });
        return after.taken() ? Duration.ZERO : after.waitForOne(rule);
    }

    /** {@code taken}: whether the call that produced this bucket got its token. */
    private record Bucket(double tokens, Instant at, boolean taken) {

        static Bucket full(Rule rule, Instant now) {
            return new Bucket(rule.capacity(), now, false);
        }

        Bucket refilled(Rule rule, Instant now) {
            long elapsedNanos = Math.max(0, Duration.between(at, now).toNanos());
            double refill = rule.capacity() * ((double) elapsedNanos / rule.period().toNanos());
            return new Bucket(Math.min(rule.capacity(), tokens + refill), now, false);
        }

        Bucket take() {
            return new Bucket(tokens - 1, at, true);
        }

        Bucket giveBack(Rule rule) {
            return new Bucket(Math.min(rule.capacity(), tokens + 1), at, false);
        }

        Duration waitForOne(Rule rule) {
            double nanosPerToken = (double) rule.period().toNanos() / rule.capacity();
            return Duration.ofNanos(Math.max(1, (long) Math.ceil((1 - tokens) * nanosPerToken)));
        }
    }
}
