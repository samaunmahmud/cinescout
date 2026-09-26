package com.cinescout.logistics.geocoding.nominatim;

import com.cinescout.logistics.LogisticsProperties;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.geocoding.Geocoder;
import com.cinescout.logistics.geocoding.RateLimitedGeocoder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wires the Nominatim {@link Geocoder}. It needs no key, so it always exists. It is rate limited here, once,
 * so every user of the bean (logistics, scouting) shares the one-request-a-second budget.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NominatimProperties.class)
class NominatimConfig {

    @Bean
    Geocoder nominatimGeocoder(WebClient.Builder builder, NominatimProperties props, LogisticsProperties logistics) {
        Geocoder nominatim = new NominatimGeocoder(
                ProviderHttp.webClient(builder, props.baseUrl(), props.timeout(), logistics.userAgent()), props);
        return new RateLimitedGeocoder(nominatim, props.minInterval(), props.maxWait());
    }
}
