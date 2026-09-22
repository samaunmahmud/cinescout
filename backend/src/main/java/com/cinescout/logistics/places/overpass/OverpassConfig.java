package com.cinescout.logistics.places.overpass;

import com.cinescout.logistics.LogisticsProperties;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.places.PlacesClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Wires the Overpass {@link PlacesClient}. It needs no key, so it always exists. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OverpassProperties.class)
class OverpassConfig {

    @Bean
    PlacesClient overpassPlacesClient(WebClient.Builder builder, OverpassProperties props, LogisticsProperties logistics) {
        return new OverpassPlacesClient(ProviderHttp.webClient(builder, props.baseUrl(), props.timeout(), logistics.userAgent()), props);
    }
}
