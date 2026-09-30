package com.cinescout.llm;

import com.cinescout.resilience.Pacer;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Keeps the calls to another {@link LlmClient} under the vendor's request rate, across the whole app (scouting,
 * parsing and outreach share it). LLM vendors cap requests per second per account (watsonx.ai's free plan allows 2)
 * and answer anything over it with 429, so a scouting run that assesses ten venues at once would lose most of them.
 *
 * <p>Calls are started at an even pace, one every {@code 1s / requestsPerSecond} (see {@link Pacer}). A call
 * waits its turn without blocking a thread; one that would wait longer than {@code maxWait} fails as
 * {@link LlmException.Kind#RATE_LIMITED} instead, without reaching the vendor.
 */
public class RateLimitedLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final Pacer pacer;

    public RateLimitedLlmClient(LlmClient delegate, int requestsPerSecond, Duration maxWait) {
        this.delegate = delegate;
        this.pacer = new Pacer(Duration.ofSeconds(1).dividedBy(requestsPerSecond), maxWait);
    }

    @Override
    public String modelId() {
        return delegate.modelId();
    }

    @Override
    public <T> Mono<T> generate(String systemPrompt, String userPrompt, Class<T> responseType) {
        return pacer.pace(() -> delegate.generate(systemPrompt, userPrompt, responseType),
                () -> new LlmException(LlmException.Kind.RATE_LIMITED, "Too many model requests are waiting; try again shortly"));
    }
}
