package com.cinescout.llm.watsonx;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * IBM watsonx.ai settings, bound from {@code cinescout.llm.watsonx.*} (environment
 * variables are mapped in {@code application.yml}).
 *
 * @param apiKey       IBM Cloud API key, exchanged for short-lived IAM tokens; a secret
 * @param projectId    watsonx.ai project the calls are billed to
 * @param modelId      foundation model, e.g. {@code ibm/granite-...}; no default, because the
 *                     available models change and a stale default fails confusingly
 * @param baseUrl      regional watsonx.ai endpoint
 * @param iamUrl       IBM Cloud IAM endpoint
 * @param apiVersion   the {@code version} date query parameter the API requires
 * @param temperature  0 keeps extraction as deterministic as the model allows
 * @param maxTokens    completion budget; an answer cut off by it is rejected, not repaired
 * @param strictSchema send the JSON schema in strict mode (the model is constrained to it);
 *                     turn off if a model rejects strict schemas
 * @param timeout      whole-call budget, including fetching an IAM token
 * @param maxRequestsPerSecond the account's request rate: 2 on the free (Lite) plan, higher on paid plans. Calls
 *                     beyond it wait their turn rather than being refused with 429
 * @param maxWait      how long a call may wait for its turn before it fails as rate-limited
 */
@Validated
@ConfigurationProperties("cinescout.llm.watsonx")
public record WatsonxProperties(
        @NotBlank String apiKey,
        @NotBlank String projectId,
        @NotBlank String modelId,
        @DefaultValue("https://us-south.ml.cloud.ibm.com") @NotBlank String baseUrl,
        @DefaultValue("https://iam.cloud.ibm.com") @NotBlank String iamUrl,
        @DefaultValue("2024-03-14") @NotBlank String apiVersion,
        @DefaultValue("0") @Min(0) @Max(2) double temperature,
        @DefaultValue("1024") @Min(1) int maxTokens,
        @DefaultValue("true") boolean strictSchema,
        @DefaultValue("60s") @NotNull Duration timeout,
        @DefaultValue("2") @Min(1) int maxRequestsPerSecond,
        @DefaultValue("30s") @NotNull Duration maxWait,
        // A model that can read pictures (scouting from a reference photo); offered in the London and Dallas regions.
        @DefaultValue("meta-llama/llama-4-maverick-17b-128e-instruct-fp8") @NotBlank String visionModelId
) {

    /** Redacts the API key so an accidental log line or failed-binding message cannot leak it. */
    @Override
    public String toString() {
        return "WatsonxProperties[projectId=" + projectId + ", modelId=" + modelId + ", visionModelId=" + visionModelId + ", baseUrl=" + baseUrl
                + ", apiVersion=" + apiVersion + ", apiKey=****]";
    }
}
