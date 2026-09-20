package com.cinescout.resilience;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Retry and circuit-breaker settings shared by every guarded dependency (each dependency
 * still gets its own breaker), bound from {@code cinescout.resilience.*}.
 *
 * <p>Each attempt has its own time budget (the client's timeout), so the worst case for one
 * guarded call is {@code maxAttempts * client timeout} plus the back-offs.
 *
 * @param maxAttempts               total tries per call, the first included; 1 disables retry
 * @param initialBackoff            wait before the first retry; doubles each time, with jitter
 * @param maxBackoff                ceiling for the wait between retries
 * @param breakerWindowSize         how many recent calls the failure rate is measured over
 * @param breakerMinimumCalls       calls needed in the window before the breaker may open
 * @param breakerFailureRatePercent failure percentage at or above which the breaker opens
 * @param breakerOpenDuration       how long it stays open (failing fast) before probing again
 */
@Validated
@ConfigurationProperties("cinescout.resilience")
public record ResilienceProperties(
        @DefaultValue("3") @Min(1) @Max(10) int maxAttempts,
        @DefaultValue("500ms") @NotNull Duration initialBackoff,
        @DefaultValue("5s") @NotNull Duration maxBackoff,
        @DefaultValue("10") @Min(2) int breakerWindowSize,
        @DefaultValue("5") @Min(1) int breakerMinimumCalls,
        @DefaultValue("50") @Min(1) @Max(100) int breakerFailureRatePercent,
        @DefaultValue("30s") @NotNull Duration breakerOpenDuration
) {
}
