package com.cinescout.logistics.weather.openmeteo;

import com.cinescout.logistics.LogisticsProperties;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.weather.WeatherClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Wires the Open-Meteo {@link WeatherClient}. It needs no key, so it always exists. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenMeteoProperties.class)
class OpenMeteoConfig {

    @Bean
    WeatherClient openMeteoWeatherClient(WebClient.Builder builder, OpenMeteoProperties props, LogisticsProperties logistics) {
        return new OpenMeteoWeatherClient(
                ProviderHttp.webClient(builder, props.forecastUrl(), props.timeout(), logistics.userAgent()),
                ProviderHttp.webClient(builder, props.archiveUrl(), props.timeout(), logistics.userAgent()),
                props);
    }
}
