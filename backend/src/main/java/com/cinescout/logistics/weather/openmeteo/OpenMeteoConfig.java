package com.cinescout.logistics.weather.openmeteo;

import com.cinescout.logistics.LogisticsProperties;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.weather.FallbackWeatherClient;
import com.cinescout.logistics.weather.WeatherClient;
import com.cinescout.logistics.weather.metno.MetNorwayProperties;
import com.cinescout.logistics.weather.metno.MetNorwayWeatherClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wires the {@link WeatherClient}: Open-Meteo, keyless, so it always exists, with MET Norway behind it for forecasts
 * Open-Meteo turns away (unless {@code cinescout.logistics.met-norway.enabled=false}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OpenMeteoProperties.class, MetNorwayProperties.class})
class OpenMeteoConfig {

    @Bean
    WeatherClient weatherClient(WebClient.Builder builder, OpenMeteoProperties props, MetNorwayProperties metNorway,
                                LogisticsProperties logistics) {
        WeatherClient openMeteo = new OpenMeteoWeatherClient(
                ProviderHttp.webClient(builder, props.forecastUrl(), props.timeout(), logistics.userAgent()),
                ProviderHttp.webClient(builder, props.archiveUrl(), props.timeout(), logistics.userAgent()),
                props);
        if (!metNorway.enabled()) {
            return openMeteo;
        }
        return new FallbackWeatherClient(openMeteo, new MetNorwayWeatherClient(
                ProviderHttp.webClient(builder, metNorway.baseUrl(), metNorway.timeout(), logistics.userAgent()), metNorway));
    }
}
