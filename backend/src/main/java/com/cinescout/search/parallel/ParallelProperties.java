package com.cinescout.search.parallel;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Parallel Search API settings, bound from {@code cinescout.search.parallel.*}.
 *
 * @param apiKey       Parallel API key, sent as {@code x-api-key}; a secret
 * @param baseUrl      API host
 * @param mode         {@code turbo}, {@code fast}, {@code basic} or {@code advanced} (slower, better);
 *                     unset leaves the choice to Parallel's default
 * @param excerptChars upper bound on the grounding excerpt kept per result
 * @param timeout      whole-call budget
 */
@Validated
@ConfigurationProperties("cinescout.search.parallel")
public record ParallelProperties(
        @NotBlank String apiKey,
        @DefaultValue("https://api.parallel.ai") @NotBlank String baseUrl,
        @Pattern(regexp = "^(turbo|fast|basic|advanced)?$", message = "must be turbo, fast, basic or advanced") String mode,
        @DefaultValue("1500") @Min(100) int excerptChars,
        @DefaultValue("30s") @NotNull Duration timeout
) {

    /** Redacts the API key so an accidental log line or failed-binding message cannot leak it. */
    @Override
    public String toString() {
        return "ParallelProperties[baseUrl=" + baseUrl + ", mode=" + mode + ", excerptChars=" + excerptChars
                + ", timeout=" + timeout + ", apiKey=****]";
    }
}
