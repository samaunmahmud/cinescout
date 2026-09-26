package com.cinescout.video;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param maxResults videos shown per venue
 * @param cacheTtl   how long a venue's videos are kept before it is searched again
 */
@Validated
@ConfigurationProperties("cinescout.video")
public record VideoProperties(
        @DefaultValue("6") @Min(1) @Max(25) int maxResults,
        @DefaultValue("7d") @NotNull Duration cacheTtl
) {
}
