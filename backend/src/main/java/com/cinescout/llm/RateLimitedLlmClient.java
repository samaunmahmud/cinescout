package com.cinescout.llm;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Keeps the calls to another {@link LlmClient} under the vendor's request rate, across the whole app (scouting,
 * parsing and outreach share it). LLM vendors cap requests per second per account (watsonx.ai's free plan allows 2)
 * and answer anything over it with 429, so a scouting run that assesses ten venues at once would lose most of them.
 * Here a call waits its turn without blocking a thread; one that would wait longer than {@code maxWait} fails as
 * {@link LlmException.Kind#RATE_LIMITED} instead, without reaching the vendor.
 */
public class RateLimitedLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final RateLimiter limiter;

    public RateLimitedLlmClient(LlmClient delegate, int requestsPerSecond, Duration maxWait) {
        this.delegate = delegate;
        this.limiter = RateLimiter.of("llm", RateLimiterConfig.custom()
                .limitForPeriod(requestsPerSecond)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(maxWait)
                .build());
    }

    @Override
    public String modelId() {
        return delegate.modelId();
    }

    @Override
    public <T> Mono<T> generate(String systemPrompt, String userPrompt, Class<T> responseType) {
        return Mono.defer(() -> delegate.generate(systemPrompt, userPrompt, responseType))
                .transformDeferred(RateLimiterOperator.of(limiter))
                .onErrorMap(RequestNotPermitted.class,
                        e -> new LlmException(LlmException.Kind.RATE_LIMITED, "Too many model requests are waiting; try again shortly", e));
    }
}
