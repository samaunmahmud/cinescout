package com.cinescout.logistics.weather;

import com.cinescout.logistics.GeoPoint;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * Daily weather for a place, behind a provider-neutral interface so the provider can change.
 *
 * <p>Implementations fail with {@link com.cinescout.logistics.LogisticsException}; they do not retry.
 * Which dates can be forecast is the caller's decision, see {@link WeatherPlan}.
 */
public interface WeatherClient {

    /** The forecast model's daily values for {@code from..to} (inclusive): upcoming days and the recent past. */
    Mono<WeatherSeries> forecast(GeoPoint point, LocalDate from, LocalDate to);

    /** Recorded (reanalysis) daily values for {@code from..to} (inclusive); only for dates at least a week old. */
    Mono<WeatherSeries> history(GeoPoint point, LocalDate from, LocalDate to);

    /** How many days after today {@link #forecast} can reach. */
    int forecastDaysAhead();

    /** How many days before today {@link #forecast} still serves; older days need {@link #history}. */
    int forecastDaysBack();

    /** The credit the data's licence requires wherever it is shown. */
    String attribution();
}
