package com.cinescout.logistics.weather;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * A primary {@link WeatherClient} with a second provider for forecasts: when the primary turns a forecast away (rate
 * limited, as a free host's shared address can be for the whole day) or is down, the forecast comes from the fallback,
 * credited to it. History, horizons and the attribution are the primary's.
 */
public class FallbackWeatherClient implements WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(FallbackWeatherClient.class);

    private final WeatherClient primary;
    private final WeatherClient fallback;

    public FallbackWeatherClient(WeatherClient primary, WeatherClient fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public Mono<WeatherSeries> forecast(GeoPoint point, LocalDate from, LocalDate to) {
        return primary.forecast(point, from, to).onErrorResume(
                error -> error instanceof LogisticsException e && e.kind() != LogisticsException.Kind.INVALID_REQUEST,
                error -> {
                    log.info("Forecast falls back to the second provider: {}", error.getMessage());
                    return fallback.forecast(point, from, to)
                            .map(series -> new WeatherSeries(series.zone(), series.days(), fallback.attribution()))
                            .onErrorResume(LogisticsException.class, second -> Mono.error(error));
                });
    }

    @Override
    public Mono<WeatherSeries> history(GeoPoint point, LocalDate from, LocalDate to) {
        return primary.history(point, from, to);
    }

    @Override
    public int forecastDaysAhead() {
        return primary.forecastDaysAhead();
    }

    @Override
    public int forecastDaysBack() {
        return primary.forecastDaysBack();
    }

    @Override
    public String attribution() {
        return primary.attribution();
    }
}
