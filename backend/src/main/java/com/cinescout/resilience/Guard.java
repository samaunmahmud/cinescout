package com.cinescout.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Protects calls to one flaky dependency with retry (exponential back-off, jittered) inside a
 * circuit breaker. Every attempt passes through the breaker, so once it opens, the remaining
 * retries fail fast instead of piling onto a dependency that is already down.
 *
 * <p>Create instances with {@link GuardFactory}.
 */
public final class Guard {

    private static final double JITTER = 0.5;

    private final CircuitBreaker breaker;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Predicate<Throwable> retryable;
    private final Function<CallNotPermittedException, ? extends Throwable> whenOpen;

    Guard(CircuitBreaker breaker, int maxAttempts, Duration initialBackoff, Duration maxBackoff,
          Predicate<Throwable> retryable, Function<CallNotPermittedException, ? extends Throwable> whenOpen) {
        this.breaker = breaker;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.retryable = retryable;
        this.whenOpen = whenOpen;
    }

    /**
     * Runs the call, retrying failures the guard considers retryable. The supplier is invoked
     * afresh for each attempt, so it must build a new {@code Mono} rather than reuse one.
     *
     * <p>When retries run out, the last failure is emitted as is, not wrapped. When the breaker
     * is open, the call is not made and the error is whatever {@code whenOpen} builds.
     */
    public <T> Mono<T> call(Supplier<Mono<T>> supplier) {
        return Mono.defer(supplier)
                .transformDeferred(CircuitBreakerOperator.of(breaker))
                .retryWhen(Retry.backoff(maxAttempts - 1L, initialBackoff)
                        .maxBackoff(maxBackoff)
                        .jitter(JITTER)
                        // An open breaker is a decision, not a blip: retrying it would only wait.
                        .filter(error -> !(error instanceof CallNotPermittedException) && retryable.test(error))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .onErrorMap(CallNotPermittedException.class, whenOpen);
    }
}
