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
    void aBurstOfCallsIsSpreadOverSecondsInsteadOfRefused() {
        LlmClient limited = new RateLimitedLlmClient(delegate, 2, Duration.ofSeconds(10));

        List<String> answers = Flux.range(0, 5).flatMap(i -> limited.generate("system", "venue " + i, String.class)).collectList().block();

        assertThat(answers).hasSize(5);
        List<Long> sorted = calledAt.stream().sorted().toList();
        // Two calls a second: the fifth falls in the third second, at least a whole second after the first.
        assertThat(Duration.ofNanos(sorted.get(4) - sorted.get(0))).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
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
