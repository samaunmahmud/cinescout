package com.cinescout.logistics.geocoding.nominatim;

import com.cinescout.logistics.LogisticsProperties;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.geocoding.Geocoder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Wires the Nominatim {@link Geocoder}. It needs no key, so it always exists. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NominatimProperties.class)
class NominatimConfig {

    @Bean
    Geocoder nominatimGeocoder(WebClient.Builder builder, NominatimProperties props, LogisticsProperties logistics) {
        return new NominatimGeocoder(ProviderHttp.webClient(builder, props.baseUrl(), props.timeout(), logistics.userAgent()), props);
    }
}
