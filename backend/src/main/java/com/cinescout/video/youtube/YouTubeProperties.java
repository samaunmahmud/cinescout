package com.cinescout.video.youtube;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * YouTube Data API settings, bound from {@code cinescout.video.youtube.*}. A search costs 100 of the free
 * 10,000 daily quota units, so results are cached per location (see {@code VideoService}).
 *
 * @param apiKey     a Google Cloud API key with the YouTube Data API v3 enabled; a secret, sent as a header
 * @param baseUrl    API host
 * @param timeout    whole-call budget
 */
@Validated
@ConfigurationProperties("cinescout.video.youtube")
public record YouTubeProperties(
        @NotBlank String apiKey,
        @DefaultValue("https://www.googleapis.com") @NotBlank String baseUrl,
        @DefaultValue("10s") @NotNull Duration timeout
) {

    /** Redacts the API key so an accidental log line or failed-binding message cannot leak it. */
    @Override
    public String toString() {
        return "YouTubeProperties[baseUrl=" + baseUrl + ", timeout=" + timeout + ", apiKey=****]";
    }
}
