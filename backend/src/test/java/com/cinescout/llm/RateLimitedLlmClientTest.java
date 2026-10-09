package com.cinescout.llm;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitedLlmClientTest {

    private final List<Long> calledAt = new CopyOnWriteArrayList<>();

    private final LlmClient delegate = new LlmClient() {
        @Override
        public String modelId() {
            return "ibm/test-model";
        }

        @Override
        public <T> Mono<T> generate(String systemPrompt, String userPrompt, Class<T> responseType) {
            calledAt.add(System.nanoTime());
            return Mono.just(responseType.cast(userPrompt));
        }
    };

    @Test
    void aBurstOfCallsIsStartedAtAnEvenPaceInsteadOfRefused() {
        LlmClient limited = new RateLimitedLlmClient(delegate, 4, Duration.ofSeconds(10));

        List<String> answers = Flux.range(0, 5).flatMap(i -> limited.generate("system", "venue " + i, String.class)).collectList().block();

        assertThat(answers).hasSize(5);
        List<Long> sorted = calledAt.stream().sorted().toList();
        // Four a second is one every 250 ms. Never two close together, which a vendor counting over a sliding
        // second would refuse. A call that starts a few ms late shortens the gap after it, so each gap gets slack
        // for the scheduler, and the run as a whole must keep to the full rate.
        for (int i = 1; i < sorted.size(); i++) {
            assertThat(Duration.ofNanos(sorted.get(i) - sorted.get(i - 1))).as("gap before call " + i).isGreaterThanOrEqualTo(Duration.ofMillis(200));
        }
        assertThat(Duration.ofNanos(sorted.getLast() - sorted.getFirst())).as("four gaps in all").isGreaterThanOrEqualTo(Duration.ofMillis(4 * 245));
    }

    @Test
    void aCallAfterAQuietSpellStartsAtOnce() {
        LlmClient limited = new RateLimitedLlmClient(delegate, 1, Duration.ofSeconds(10));
        limited.generate("system", "first", String.class).block();
        long before = System.nanoTime();

        // Not yet a second later: this one waits. It does not get two turns for the quiet time before the first.
        limited.generate("system", "second", String.class).block();

        assertThat(Duration.ofNanos(System.nanoTime() - before)).isGreaterThanOrEqualTo(Duration.ofMillis(900));
        assertThat(Duration.ofNanos(calledAt.get(0) - before)).isLessThan(Duration.ofMillis(200));
    }

    @Test
    void aCallThatWouldWaitTooLongFailsAsRateLimitedWithoutReachingTheModel() {
        LlmClient limited = new RateLimitedLlmClient(delegate, 1, Duration.ofMillis(50));
        limited.generate("system", "first", String.class).block();

        assertThatThrownBy(() -> limited.generate("system", "second", String.class).block())
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.kind()).isEqualTo(LlmException.Kind.RATE_LIMITED);
                    assertThat(e.isRetryable()).isTrue();
                });
        assertThat(calledAt).hasSize(1);
    }

    @Test
    void keepsTheModelsId() {
        assertThat(new RateLimitedLlmClient(delegate, 2, Duration.ofSeconds(1)).modelId()).isEqualTo("ibm/test-model");
    }
}
