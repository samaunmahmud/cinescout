package com.cinescout.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardTest {

    /** A failure the dependency's outage would cause: retried, and it trips the breaker. */
    static class Outage extends RuntimeException {
        Outage(String message) {
            super(message);
        }
    }

    /** A failure that means "the dependency answered, and said no": neither retried nor tripping. */
    static class Rejected extends RuntimeException {
    }

    private CircuitBreakerRegistry registry;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        registry = CircuitBreakerRegistry.ofDefaults();
    }

    private Guard guard(int maxAttempts, int window) {
        return guard("dep", maxAttempts, window);
    }

    private Guard guard(String name, int maxAttempts, int window) {
        // Back-offs of a millisecond keep the retry tests fast; the window is also the minimum calls.
        ResilienceProperties props = new ResilienceProperties(maxAttempts, Duration.ofMillis(1), Duration.ofMillis(5),
                window, window, 50, Duration.ofSeconds(30));
        return new GuardFactory(props, registry).create(name,
                error -> error instanceof Outage,
                error -> error instanceof Outage,
                open -> new IllegalStateException("dependency unavailable"));
    }

    private CircuitBreaker breaker() {
        return registry.circuitBreaker("dep");
    }

    private Mono<String> failing(RuntimeException error) {
        return Mono.defer(() -> {
            calls.incrementAndGet();
            return Mono.error(error);
        });
    }

    // --- retry ------------------------------------------------------------------------------

    @Test
    void aSuccessfulCallRunsOnce() {
        String result = guard(3, 10).call(() -> {
            calls.incrementAndGet();
            return Mono.just("ok");
        }).block();

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesARetryableFailureUntilItSucceeds() {
        String result = guard(3, 10).call(() -> calls.incrementAndGet() < 3
                ? Mono.<String>error(new Outage("blip"))
                : Mono.just("ok")).block();

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void doesNotRetryAFailureThatIsNotRetryable() {
        Rejected rejected = new Rejected();

        assertThatThrownBy(() -> guard(3, 10).call(() -> failing(rejected)).block()).isSameAs(rejected);
        assertThat(calls).hasValue(1);
    }

    @Test
    void whenRetriesRunOutTheOriginalFailureIsEmittedNotAWrapper() {
        Outage outage = new Outage("still down");

        assertThatThrownBy(() -> guard(3, 10).call(() -> failing(outage)).block()).isSameAs(outage);
        assertThat(calls).hasValue(3);
    }

    @Test
    void oneAttemptMeansNoRetry() {
        assertThatThrownBy(() -> guard(1, 10).call(() -> failing(new Outage("down"))).block()).isInstanceOf(Outage.class);
        assertThat(calls).hasValue(1);
    }

    // --- circuit breaker --------------------------------------------------------------------

    @Test
    void afterEnoughFailuresTheBreakerOpensAndCallsFailFastWithoutReachingTheDependency() {
        Guard guard = guard(1, 4);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> guard.call(() -> failing(new Outage("down"))).block()).isInstanceOf(Outage.class);
        }
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsBefore = calls.get();

        assertThatThrownBy(() -> guard.call(() -> failing(new Outage("down"))).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("dependency unavailable");
        assertThat(calls).hasValue(callsBefore);
    }

    @Test
    void retryingStopsAsSoonAsTheBreakerOpensMidRetry() {
        // Window of 2: the second failure opens the breaker, so attempt 3 of 5 is refused, not retried.
        assertThatThrownBy(() -> guard(5, 2).call(() -> failing(new Outage("down"))).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("dependency unavailable");
        assertThat(calls).hasValue(2);
    }

    @Test
    void anOpenBreakerIsNeverRetriedEvenWhenEveryFailureIsRetryable() {
        ResilienceProperties props = new ResilienceProperties(5, Duration.ofMillis(1), Duration.ofMillis(5),
                2, 2, 50, Duration.ofSeconds(30));
        Guard retryEverything = new GuardFactory(props, registry).create("dep",
                error -> true, error -> error instanceof Outage, open -> new IllegalStateException("dependency unavailable"));

        assertThatThrownBy(() -> retryEverything.call(() -> failing(new Outage("down"))).block())
                .isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(2);
        // Retrying a refused call never reaches the dependency, but it would still wait out the
        // back-off each time; the breaker's own counter shows the refusal was not retried.
        assertThat(breaker().getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
    }

    @Test
    void failuresThatAreNotOutagesNeverOpenTheBreaker() {
        Guard guard = guard(1, 4);

        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(() -> guard.call(() -> failing(new Rejected())).block()).isInstanceOf(Rejected.class);
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void theBreakerClosesAgainOnceProbeCallsSucceed() {
        Guard guard = guard(1, 4);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> guard.call(() -> failing(new Outage("down"))).block()).isInstanceOf(Outage.class);
        }
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        breaker().transitionToHalfOpenState(); // what the open duration elapsing would do
        assertThat(guard.call(() -> Mono.just("probe 1")).block()).isEqualTo("probe 1");
        assertThat(guard.call(() -> Mono.just("probe 2")).block()).isEqualTo("probe 2");

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void aFailedProbeReopensTheBreaker() {
        Guard guard = guard(1, 4);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> guard.call(() -> failing(new Outage("down"))).block()).isInstanceOf(Outage.class);
        }
        breaker().transitionToHalfOpenState();

        assertThatThrownBy(() -> guard.call(() -> failing(new Outage("still down"))).block()).isInstanceOf(Outage.class);
        assertThatThrownBy(() -> guard.call(() -> failing(new Outage("still down"))).block()).isInstanceOf(Outage.class);

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void guardsWithTheSameNameShareOneBreaker() {
        Guard first = guard("dep", 1, 4);
        Guard second = guard("dep", 1, 4);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> first.call(() -> failing(new Outage("down"))).block()).isInstanceOf(Outage.class);
        }
        int callsBefore = calls.get();

        assertThatThrownBy(() -> second.call(() -> failing(new Outage("down"))).block())
                .isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(callsBefore);
    }

    @Test
    void guardsWithDifferentNamesDoNotAffectEachOther() {
        Guard llm = guard("llm", 1, 4);
        Guard search = guard("search", 1, 4);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> llm.call(() -> failing(new Outage("down"))).block()).isInstanceOf(Outage.class);
        }

        assertThat(search.call(() -> Mono.just("fine")).block()).isEqualTo("fine");
    }
}
