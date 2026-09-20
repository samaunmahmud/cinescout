package com.cinescout.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import java.util.function.Predicate;

/** Builds {@link Guard}s from the shared {@link ResilienceProperties}, one breaker per name. */
public class GuardFactory {

    private static final Logger log = LoggerFactory.getLogger(GuardFactory.class);

    private final ResilienceProperties props;
    private final CircuitBreakerRegistry registry;

    public GuardFactory(ResilienceProperties props, CircuitBreakerRegistry registry) {
        this.props = props;
        this.registry = registry;
    }

    /**
     * @param name           identifies the breaker (and its log lines); the same name shares one breaker
     * @param retryable      which failures are worth another attempt
     * @param tripsBreaker   which failures count toward opening the breaker. Any other failure
     *                       is recorded as a success: a dependency that answers "no" is not down
     * @param whenOpen       the error to emit, without calling the dependency, while the breaker is open
     */
    public Guard create(String name, Predicate<Throwable> retryable, Predicate<Throwable> tripsBreaker,
                        Function<CallNotPermittedException, ? extends Throwable> whenOpen) {
        boolean existed = registry.find(name).isPresent();
        CircuitBreaker breaker = registry.circuitBreaker(name, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(props.breakerWindowSize())
                .minimumNumberOfCalls(Math.min(props.breakerMinimumCalls(), props.breakerWindowSize()))
                .failureRateThreshold(props.breakerFailureRatePercent())
                .waitDurationInOpenState(props.breakerOpenDuration())
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordException(tripsBreaker)
                .build());
        if (!existed) {
            breaker.getEventPublisher().onStateTransition(event ->
                    log.warn("Circuit breaker '{}' moved {}", name, event.getStateTransition()));
        }
        return new Guard(breaker, props.maxAttempts(), props.initialBackoff(), props.maxBackoff(), retryable, whenOpen);
    }
}
