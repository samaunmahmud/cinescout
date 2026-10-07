package com.cinescout.logistics.places.overpass;

import com.cinescout.logistics.LogisticsProperties;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.places.FallbackPlacesClient;
import com.cinescout.logistics.places.PlacesClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Wires the Overpass {@link PlacesClient}. It needs no key, so it always exists; with a fallback server set, a query
 * the first server turns away at once goes to the second.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OverpassProperties.class)
class OverpassConfig {

    /** Long enough for a refusal, a 429 or a 504 and the client's own one retry; shorter than a query that timed out. */
    static final Duration QUICK_FAILURE = Duration.ofSeconds(10);

    @Bean
    PlacesClient overpassPlacesClient(WebClient.Builder builder, OverpassProperties props, LogisticsProperties logistics) {
        PlacesClient primary = new OverpassPlacesClient(ProviderHttp.webClient(builder, props.baseUrl(), props.timeout(), logistics.userAgent()), props);
        if (props.fallbackUrl() == null || props.fallbackUrl().isBlank()) {
            return primary;
        }
        PlacesClient fallback = new OverpassPlacesClient(ProviderHttp.webClient(builder, props.fallbackUrl(), props.timeout(), logistics.userAgent()), props);
        return new FallbackPlacesClient(primary, fallback, QUICK_FAILURE);
    }
}
