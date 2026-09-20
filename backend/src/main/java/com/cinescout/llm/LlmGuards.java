package com.cinescout.llm;

import com.cinescout.resilience.Guard;
import com.cinescout.resilience.GuardFactory;

/**
 * The one resilience policy for calls to the {@link LlmClient}, shared by everything that uses it.
 * The guard is named {@code "llm"}, so all callers share a single circuit breaker: an outage seen by
 * scouting also stops outreach from hammering the same model, and the other way round.
 *
 * <p>Only outages trip the breaker: a model that returns unusable JSON, or a request the vendor
 * rejects, means "no" to this call, not "down". Unusable output is still worth a retry.
 */
public final class LlmGuards {

    private LlmGuards() {
    }

    public static Guard create(GuardFactory guards) {
        return guards.create("llm",
                error -> error instanceof LlmException e && e.isRetryable(),
                error -> error instanceof LlmException e
                        && (e.kind() == LlmException.Kind.UNAVAILABLE || e.kind() == LlmException.Kind.RATE_LIMITED),
                open -> new LlmException(LlmException.Kind.UNAVAILABLE,
                        "The LLM circuit breaker is open; the call was not made", open));
    }
}
