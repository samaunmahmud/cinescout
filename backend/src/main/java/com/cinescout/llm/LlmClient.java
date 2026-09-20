package com.cinescout.llm;

import reactor.core.publisher.Mono;

/**
 * Provider-neutral access to a large language model. Implementations (currently IBM
 * watsonx.ai) hide authentication, wire format and vendor error codes, so the pipeline
 * never depends on one vendor.
 *
 * <p>Prompts are owned by the callers (the orchestration service); this client only
 * guarantees the shape of what comes back.
 */
public interface LlmClient {

    /** The id of the model that answers, recorded next to what it wrote (e.g. on an outreach draft). */
    String modelId();

    /**
     * Asks the model for a JSON answer matching {@code responseType} and returns it parsed
     * and bean-validated. Model output is untrusted: anything malformed, truncated or failing
     * the type's Jakarta constraints is rejected as {@link LlmException.Kind#INVALID_OUTPUT}
     * rather than returned.
     *
     * @param systemPrompt instructions for the model
     * @param userPrompt   the content to work on; treated as data, never logged
     * @param responseType a record (or bean) whose fields define the JSON contract
     * @return the parsed answer, or an error signal carrying an {@link LlmException}
     */
    <T> Mono<T> generate(String systemPrompt, String userPrompt, Class<T> responseType);
}
