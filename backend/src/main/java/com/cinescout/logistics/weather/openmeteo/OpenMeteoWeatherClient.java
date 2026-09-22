package com.cinescout.logistics.weather.openmeteo;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.weather.DailyWeather;
import com.cinescout.logistics.weather.WeatherClient;
import com.cinescout.logistics.weather.WeatherSeries;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link WeatherClient} backed by Open-Meteo: the forecast API ({@code GET /v1/forecast}) and the
 * historical weather API ({@code GET /v1/archive}), both keyless. Values are metric (degrees Celsius,
 * millimetres, km/h), which is Open-Meteo's default. {@code timezone=auto} makes it resolve the
 * location's time zone, which the solar times are then shown in.
 */
public class OpenMeteoWeatherClient implements WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherClient.class);

    private static final String SERVICE = "Open-Meteo";
    private static final String FORECAST_DAILY = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,"
            + "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,cloud_cover_mean";
    // The archive has no precipitation probability: it records what happened.
    private static final String HISTORY_DAILY = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,"
            + "wind_speed_10m_max,wind_gusts_10m_max,cloud_cover_mean";

    private final WebClient forecastApi;
    private final WebClient archiveApi;
    private final OpenMeteoProperties props;

    /** @param forecastApi a client for the forecast host; {@code archiveApi} one for the archive host */
    public OpenMeteoWeatherClient(WebClient forecastApi, WebClient archiveApi, OpenMeteoProperties props) {
        this.forecastApi = forecastApi;
        this.archiveApi = archiveApi;
        this.props = props;
    }

    @Override
    public Mono<WeatherSeries> forecast(GeoPoint point, LocalDate from, LocalDate to) {
        return daily(forecastApi, "/v1/forecast", FORECAST_DAILY, point, from, to);
    }

    @Override
    public Mono<WeatherSeries> history(GeoPoint point, LocalDate from, LocalDate to) {
        return daily(archiveApi, "/v1/archive", HISTORY_DAILY, point, from, to);
    }

    @Override
    public int forecastDaysAhead() {
        return props.forecastDaysAhead();
    }

    @Override
    public int forecastDaysBack() {
        return props.forecastDaysBack();
    }

    @Override
    public String attribution() {
        return "Weather data by Open-Meteo.com (CC BY 4.0)";
    }

    private Mono<WeatherSeries> daily(WebClient api, String path, String variables, GeoPoint point, LocalDate from, LocalDate to) {
        Mono<WeatherSeries> call = api.get()
                .uri(uri -> uri.path(path)
                        .queryParam("latitude", ProviderHttp.coordinate(point.latitude()))
                        .queryParam("longitude", ProviderHttp.coordinate(point.longitude()))
                        .queryParam("daily", variables)
                        .queryParam("timezone", "auto")
                        .queryParam("start_date", from)
                        .queryParam("end_date", to)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toException)
                .bodyToMono(Response.class)
                .switchIfEmpty(Mono.error(() -> new LogisticsException(Kind.UNAVAILABLE, SERVICE + " returned an empty response")))
                .map(this::toSeries);
        return ProviderHttp.guardTransport(call, SERVICE, props.timeout());
    }

    private Mono<Throwable> toException(ClientResponse response) {
        int status = response.statusCode().value();
        // Open-Meteo explains a 400 in {"error": true, "reason": "..."}; the reason names parameters, not places.
        return response.bodyToMono(Response.class)
                .map(body -> body.reason() == null ? "" : ": " + body.reason())
                .onErrorReturn("")
                .defaultIfEmpty("")
                .map(reason -> {
                    LogisticsException error = LogisticsException.forStatus(SERVICE, status);
                    return new LogisticsException(error.kind(), error.getMessage() + reason);
                });
    }

    private WeatherSeries toSeries(Response response) {
        ZoneId zone = zoneOf(response.timezone());
        Daily daily = response.daily();
        if (daily == null || daily.time() == null) {
            throw new LogisticsException(Kind.UNAVAILABLE, SERVICE + " returned no daily values");
        }
        List<DailyWeather> days = new ArrayList<>();
        for (int i = 0; i < daily.time().size(); i++) {
            days.add(new DailyWeather(
                    daily.time().get(i),
                    at(daily.weatherCode(), i),
                    at(daily.temperatureMax(), i),
                    at(daily.temperatureMin(), i),
                    at(daily.precipitationSum(), i),
                    at(daily.precipitationProbabilityMax(), i),
                    at(daily.windSpeedMax(), i),
                    at(daily.windGustsMax(), i),
                    at(daily.cloudCoverMean(), i)));
        }
        return new WeatherSeries(zone, List.copyOf(days));
    }

    /** The resolved time zone, or UTC if Open-Meteo sent none or one Java does not know. */
    private static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            log.warn("{} returned an unknown time zone '{}'; using UTC", SERVICE, timezone);
            return ZoneId.of("UTC");
        }
    }

    /** The i-th value of a column that may be missing or short; values themselves may be null (no data). */
    private static <T> T at(List<T> column, int i) {
        return column == null || i >= column.size() ? null : column.get(i);
    }

    // --- wire format ---------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(String timezone, Daily daily, String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Daily(List<LocalDate> time,
                 @JsonProperty("weather_code") List<Integer> weatherCode,
                 @JsonProperty("temperature_2m_max") List<Double> temperatureMax,
                 @JsonProperty("temperature_2m_min") List<Double> temperatureMin,
                 @JsonProperty("precipitation_sum") List<Double> precipitationSum,
                 @JsonProperty("precipitation_probability_max") List<Integer> precipitationProbabilityMax,
                 @JsonProperty("wind_speed_10m_max") List<Double> windSpeedMax,
                 @JsonProperty("wind_gusts_10m_max") List<Double> windGustsMax,
                 @JsonProperty("cloud_cover_mean") List<Integer> cloudCoverMean) {
    }
}
