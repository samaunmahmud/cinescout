package com.cinescout.llm;

import com.cinescout.llm.LlmException.Kind;
import com.cinescout.resilience.Guard;
import com.cinescout.resilience.GuardFactory;
import com.cinescout.resilience.ResilienceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmGuardsTest {

    private final GuardFactory factory = new GuardFactory(
            new ResilienceProperties(1, Duration.ofMillis(1), Duration.ofMillis(5), 2, 2, 50, Duration.ofSeconds(30)),
            CircuitBreakerRegistry.ofDefaults());
    private final AtomicInteger calls = new AtomicInteger();

    private Mono<String> failing(Kind kind) {
        return Mono.defer(() -> {
            calls.incrementAndGet();
            return Mono.error(new LlmException(kind, "boom"));
        });
    }

    @Test
    void everyCallerOfTheLlmSharesOneBreaker() {
        Guard scouting = LlmGuards.create(factory);
        Guard outreach = LlmGuards.create(factory);

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> scouting.call(() -> failing(Kind.UNAVAILABLE)).block()).isInstanceOf(LlmException.class);
        }
        int before = calls.get();

        // The outage scouting saw stops outreach from calling the model at all.
        assertThatThrownBy(() -> outreach.call(() -> failing(Kind.UNAVAILABLE)).block())
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("circuit breaker is open");
        assertThat(calls.get()).isEqualTo(before);
    }

    @Test
    void aModelThatSaysNoIsNotAnOutage() {
        Guard guard = LlmGuards.create(factory);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> guard.call(() -> failing(Kind.INVALID_REQUEST)).block()).isInstanceOf(LlmException.class);
        }

        // Still closed: every call reached the model and got its own answer.
        assertThat(calls.get()).isEqualTo(5);
    }

    @Test
    void beingToldToSlowDownIsNotAnOutageEither() {
        Guard guard = LlmGuards.create(factory);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> guard.call(() -> failing(Kind.RATE_LIMITED)).block()).isInstanceOf(LlmException.class);
        }

        // Still closed: a burst of 429s must not stop the calls behind it from reaching the model.
        assertThat(calls.get()).isEqualTo(5);
    }
}
