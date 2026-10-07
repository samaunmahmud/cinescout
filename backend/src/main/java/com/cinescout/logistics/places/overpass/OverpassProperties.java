package com.cinescout.logistics.places.overpass;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Overpass API settings, bound from {@code cinescout.logistics.overpass.*}. The public instance is free
 * and keyless but shared: it allows a couple of queries at a time per client, and a location's query takes
 * several seconds in a dense city. Anything beyond light use should point {@code base-url} at a private
 * instance.
 *
 * @param baseUrl              Overpass host
 * @param serverTimeoutSeconds how long Overpass may run the query
 * @param timeout              whole-call budget; a little longer than the server's, so its own answer arrives
 * @param retryPause           how long to wait before the one second try after the server turned a query away at
 *                             once; see {@code OverpassPlacesClient}
 * @param unitBaseRadius       how far from a venue to look for somewhere to park the trucks, in metres
 * @param fallbackUrl          a second Overpass server, asked when the first turns a query away at once (a free host's
 *                             shared address can be refused outright); blank for none
 */
@Validated
@ConfigurationProperties("cinescout.logistics.overpass")
public record OverpassProperties(
        @DefaultValue("https://overpass-api.de") @NotBlank String baseUrl,
        @DefaultValue("25") @Min(5) @Max(180) int serverTimeoutSeconds,
        @DefaultValue("30s") @NotNull Duration timeout,
        @DefaultValue("2s") @NotNull Duration retryPause,
        @DefaultValue("1000") @Min(200) @Max(3000) int unitBaseRadius,
        @DefaultValue("https://overpass.openstreetmap.fr") String fallbackUrl
) {
}
