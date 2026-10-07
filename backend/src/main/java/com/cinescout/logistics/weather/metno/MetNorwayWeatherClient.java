package com.cinescout.logistics.weather.metno;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.weather.DailyWeather;
import com.cinescout.logistics.weather.WeatherClient;
import com.cinescout.logistics.weather.WeatherSeries;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Forecasts from MET Norway's Locationforecast 2.0 ({@code GET /weatherapi/locationforecast/2.0/complete}), keyless.
 * It answers a time series: hourly for about two and a half days, then every six hours to about ten days, in UTC.
 * This client sums it into days by UTC date, so it gives no time zone. Wind arrives in m/s and is turned into km/h.
 * Outside the Nordic countries there is usually no chance of rain, only amounts. It has no history.
 */
public class MetNorwayWeatherClient implements WeatherClient {

    private static final String SERVICE = "MET Norway";
    private static final double KMH_PER_MS = 3.6;

    private final WebClient api;
    private final MetNorwayProperties props;

    public MetNorwayWeatherClient(WebClient api, MetNorwayProperties props) {
        this.api = api;
        this.props = props;
    }

    @Override
    public Mono<WeatherSeries> forecast(GeoPoint point, LocalDate from, LocalDate to) {
        Mono<WeatherSeries> call = api.get()
                .uri(uri -> uri.path("/weatherapi/locationforecast/2.0/complete")
                        // The terms ask for at most four decimals, so nearby requests share the cache.
                        .queryParam("lat", String.format(Locale.ROOT, "%.4f", point.latitude()))
                        .queryParam("lon", String.format(Locale.ROOT, "%.4f", point.longitude()))
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> Mono.just(LogisticsException.forStatus(SERVICE, response.statusCode().value())))
                .bodyToMono(Response.class)
                .switchIfEmpty(Mono.error(() -> new LogisticsException(Kind.UNAVAILABLE, SERVICE + " returned an empty response")))
                .map(response -> new WeatherSeries(null, daily(response, from, to)));
        return ProviderHttp.guardTransport(call, SERVICE, props.timeout());
    }

    @Override
    public Mono<WeatherSeries> history(GeoPoint point, LocalDate from, LocalDate to) {
        return Mono.error(new LogisticsException(Kind.INVALID_REQUEST, SERVICE + " has no recorded weather"));
    }

    @Override
    public int forecastDaysAhead() {
        return 9;
    }

    @Override
    public int forecastDaysBack() {
        return 0;
    }

    @Override
    public String attribution() {
        return "Weather data from MET Norway (CC BY 4.0)";
    }

    /** The series summed into UTC days from {@code from} to {@code to}; days it does not reach are left out. */
    static List<DailyWeather> daily(Response response, LocalDate from, LocalDate to) {
        if (response.properties() == null || response.properties().timeseries() == null) {
            throw new LogisticsException(Kind.UNAVAILABLE, SERVICE + " returned no time series");
        }
        List<Step> steps = response.properties().timeseries();
        Map<LocalDate, Day> days = new TreeMap<>();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (step.time() == null || step.data() == null) {
                continue;
            }
            LocalDate date = LocalDate.ofInstant(step.time(), ZoneOffset.UTC);
            if (date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            long hours = i + 1 < steps.size() && steps.get(i + 1).time() != null
                    ? Duration.between(step.time(), steps.get(i + 1).time()).toHours() : 6;
            days.computeIfAbsent(date, key -> new Day()).add(step.data(), hours);
        }
        List<DailyWeather> result = new ArrayList<>();
        days.forEach((date, day) -> result.add(day.toDaily(date)));
        return List.copyOf(result);
    }

    /** What one UTC day's steps add up to. */
    private static final class Day {
        private Double max;
        private Double min;
        private double rain;
        private boolean anyRain;
        private Integer chance;
        private Double wind;
        private Double gust;
        private double cloudSum;
        private int cloudCount;
        private Integer code;

        void add(Data data, long hoursToNext) {
            Details now = data.instant() == null ? null : data.instant().details();
            if (now != null) {
                max = higher(max, now.airTemperature());
                min = lower(min, now.airTemperature());
                wind = higher(wind, now.windSpeed() == null ? null : now.windSpeed() * KMH_PER_MS);
                gust = higher(gust, now.windSpeedOfGust() == null ? null : now.windSpeedOfGust() * KMH_PER_MS);
                if (now.cloudAreaFraction() != null) {
                    cloudSum += now.cloudAreaFraction();
                    cloudCount++;
                }
            }
            // Each step's own period: the next hour while the series is hourly, the next six hours after that (or the
            // next hour when that is all the step has), so no rain is counted twice.
            Period period = hoursToNext <= 1 || data.next6Hours() == null ? data.next1Hours() : data.next6Hours();
            if (period != null && period.details() != null && period.details().precipitationAmount() != null) {
                rain += period.details().precipitationAmount();
                anyRain = true;
            }
            if (data.next6Hours() != null && data.next6Hours().details() != null) {
                max = higher(max, data.next6Hours().details().airTemperatureMax());
                min = lower(min, data.next6Hours().details().airTemperatureMin());
            }
            for (Period p : new Period[]{data.next1Hours(), data.next6Hours(), data.next12Hours()}) {
                if (p != null && p.details() != null && p.details().probabilityOfPrecipitation() != null) {
                    chance = chance == null ? (int) Math.round(p.details().probabilityOfPrecipitation())
                            : Math.max(chance, (int) Math.round(p.details().probabilityOfPrecipitation()));
                }
                if (p != null && p.summary() != null) {
                    Integer wmo = wmo(p.summary().symbolCode());
                    if (wmo != null && (code == null || wmo > code)) {
                        code = wmo;
                    }
                }
            }
        }

        DailyWeather toDaily(LocalDate date) {
            return new DailyWeather(date, code, round(max), round(min), anyRain ? Math.round(rain * 10) / 10.0 : null, chance,
                    round(wind), round(gust), cloudCount == 0 ? null : (int) Math.round(cloudSum / cloudCount));
        }

        private static Double higher(Double a, Double b) {
            if (b == null) {
                return a;
            }
            return a == null ? b : Double.valueOf(Math.max(a, b));
        }

        private static Double lower(Double a, Double b) {
            if (b == null) {
                return a;
            }
            return a == null ? b : Double.valueOf(Math.min(a, b));
        }

        private static Double round(Double value) {
            return value == null ? null : Math.round(value * 10) / 10.0;
        }
    }

    /**
     * A MET Norway symbol ("lightrainshowers_day") as the nearest WMO weather code, the scale the rest of the app speaks;
     * a higher code is worse weather, so a day takes its highest. Null for a symbol it does not know.
     */
    static Integer wmo(String symbol) {
        if (symbol == null) {
            return null;
        }
        String base = symbol.replaceAll("_(day|night|polartwilight)$", "");
        if (base.contains("thunder")) {
            return 95;
        }
        return switch (base) {
            case "clearsky" -> 0;
            case "fair" -> 1;
            case "partlycloudy" -> 2;
            case "cloudy" -> 3;
            case "fog" -> 45;
            case "lightrain" -> 61;
            case "rain" -> 63;
            case "heavyrain" -> 65;
            case "lightsleet", "sleet", "heavysleet", "lightsleetshowers", "sleetshowers", "heavysleetshowers" -> 66;
            case "lightsnow" -> 71;
            case "snow" -> 73;
            case "heavysnow" -> 75;
            case "lightrainshowers" -> 80;
            case "rainshowers" -> 81;
            case "heavyrainshowers" -> 82;
            case "lightsnowshowers", "snowshowers" -> 85;
            case "heavysnowshowers" -> 86;
            default -> null;
        };
    }

    // --- wire format ---------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(Properties properties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Properties(List<Step> timeseries) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Step(Instant time, Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(Moment instant,
                @JsonProperty("next_1_hours") Period next1Hours,
                @JsonProperty("next_6_hours") Period next6Hours,
                @JsonProperty("next_12_hours") Period next12Hours) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Moment(Details details) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Period(Summary summary, Details details) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Summary(@JsonProperty("symbol_code") String symbolCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Details(@JsonProperty("air_temperature") Double airTemperature,
                   @JsonProperty("air_temperature_max") Double airTemperatureMax,
                   @JsonProperty("air_temperature_min") Double airTemperatureMin,
                   @JsonProperty("wind_speed") Double windSpeed,
                   @JsonProperty("wind_speed_of_gust") Double windSpeedOfGust,
                   @JsonProperty("cloud_area_fraction") Double cloudAreaFraction,
                   @JsonProperty("precipitation_amount") Double precipitationAmount,
                   @JsonProperty("probability_of_precipitation") Double probabilityOfPrecipitation) {
    }
}
