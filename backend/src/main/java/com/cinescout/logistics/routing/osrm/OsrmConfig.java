package com.cinescout.logistics.routing.osrm;

import com.cinescout.logistics.LogisticsProperties;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.routing.RateLimitedRoutingClient;
import com.cinescout.logistics.routing.RoutingClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Wires the OSRM {@link RoutingClient}: keyless, so always there, and paced once for the whole app. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OsrmProperties.class)
class OsrmConfig {

    @Bean
    RoutingClient osrmRoutingClient(WebClient.Builder builder, OsrmProperties props, LogisticsProperties logistics) {
        RoutingClient osrm = new OsrmRoutingClient(ProviderHttp.webClient(builder, props.baseUrl(), props.timeout(), logistics.userAgent()), props);
        return new RateLimitedRoutingClient(osrm, props.minInterval(), props.maxWait());
    }
}
