package com.cinescout.logistics.weather.metno;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.weather.DailyWeather;
import com.cinescout.logistics.weather.WeatherSeries;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** The body is a trimmed copy of a real Locationforecast answer for East London (captured 2026-10-07). */
class MetNorwayWeatherClientTest {

    private static final GeoPoint DOCKS = new GeoPoint(51.507912, 0.018734);

    // Hourly on 8 October, then six-hourly on the 12th; one step on the 13th past the range asked for.
    private static final String FORECAST = """
            {"type":"Feature","properties":{"meta":{"updated_at":"2026-10-07T19:17:26Z"},"timeseries":[
             {"time":"2026-10-08T12:00:00Z","data":{"instant":{"details":{"air_temperature":14.2,"cloud_area_fraction":80.0,"wind_speed":6.0}},
               "next_1_hours":{"summary":{"symbol_code":"cloudy"},"details":{"precipitation_amount":0.0}},
               "next_6_hours":{"summary":{"symbol_code":"rain"},"details":{"air_temperature_max":15.0,"air_temperature_min":12.0,"precipitation_amount":9.9}}}},
             {"time":"2026-10-08T13:00:00Z","data":{"instant":{"details":{"air_temperature":15.1,"cloud_area_fraction":100.0,"wind_speed":12.5,"wind_speed_of_gust":18.0}},
               "next_1_hours":{"summary":{"symbol_code":"lightrainshowers_day"},"details":{"precipitation_amount":1.2}},
               "next_6_hours":{"summary":{"symbol_code":"rain"},"details":{"precipitation_amount":9.9}}}},
             {"time":"2026-10-08T14:00:00Z","data":{"instant":{"details":{"air_temperature":13.0,"cloud_area_fraction":90.0,"wind_speed":8.0}},
               "next_1_hours":{"summary":{"symbol_code":"rain"},"details":{"precipitation_amount":4.5}}}},
             {"time":"2026-10-12T06:00:00Z","data":{"instant":{"details":{"air_temperature":7.9,"cloud_area_fraction":90.6,"wind_speed":1.1}},
               "next_6_hours":{"summary":{"symbol_code":"cloudy"},"details":{"air_temperature_max":16.7,"air_temperature_min":7.9,"precipitation_amount":0.0,
                               "probability_of_precipitation":12.0}}}},
             {"time":"2026-10-12T12:00:00Z","data":{"instant":{"details":{"air_temperature":16.0,"cloud_area_fraction":50.0,"wind_speed":3.0}},
               "next_6_hours":{"summary":{"symbol_code":"partlycloudy_day"},"details":{"precipitation_amount":0.4,"probability_of_precipitation":35.0}}}},
             {"time":"2026-10-13T00:00:00Z","data":{"instant":{"details":{"air_temperature":8.8,"wind_speed":1.3}}}}]}}""";

    @RegisterExtension
    static WireMockExtension api = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private MetNorwayWeatherClient client() {
        MetNorwayProperties props = new MetNorwayProperties(true, api.baseUrl(), Duration.ofSeconds(5));
        return new MetNorwayWeatherClient(ProviderHttp.webClient(WebClient.builder(), api.baseUrl(), props.timeout(), "CineScout-test"), props);
    }

    private void stub(int status, String body) {
        api.stubFor(get(urlPathEqualTo("/weatherapi/locationforecast/2.0/complete")).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    void theTimeSeriesIsSummedIntoUtcDaysWithWindInKmhAndTheWorstSymbol() {
        stub(200, FORECAST);

        WeatherSeries series = client().forecast(DOCKS, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 12)).block();

        assertThat(series.zone()).isNull();
        List<DailyWeather> days = series.days();
        assertThat(days).extracting(DailyWeather::date).containsExactly(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 12));
        DailyWeather wet = days.getFirst();
        // The hourly steps count their own hour; the last step of the day (no next step that day) its six hours.
        assertThat(wet.precipitationMm()).isEqualTo(5.7);
        assertThat(wet.precipitationProbabilityPercent()).isNull();
        assertThat(wet.windSpeedMaxKmh()).isEqualTo(45.0);
        assertThat(wet.windGustsMaxKmh()).isEqualTo(64.8);
        assertThat(wet.temperatureMaxC()).isEqualTo(15.1);
        assertThat(wet.temperatureMinC()).isEqualTo(12.0);
        assertThat(wet.cloudCoverPercent()).isEqualTo(90);
        assertThat(wet.weatherCode()).isEqualTo(80); // the highest code of the day's symbols: light rain showers
        DailyWeather dry = days.getLast();
        assertThat(dry.precipitationMm()).isEqualTo(0.4);
        assertThat(dry.precipitationProbabilityPercent()).isEqualTo(35);
        assertThat(dry.temperatureMaxC()).isEqualTo(16.7);
        assertThat(dry.weatherCode()).isEqualTo(3);

        api.verify(getRequestedFor(urlPathEqualTo("/weatherapi/locationforecast/2.0/complete"))
                .withQueryParam("lat", equalTo("51.5079")).withQueryParam("lon", equalTo("0.0187"))
                .withHeader("User-Agent", equalTo("CineScout-test")));
    }

    @Test
    void symbolsMapToTheNearestWmoCode() {
        assertThat(MetNorwayWeatherClient.wmo("clearsky_night")).isZero();
        assertThat(MetNorwayWeatherClient.wmo("heavyrainandthunder")).isEqualTo(95);
        assertThat(MetNorwayWeatherClient.wmo("fog")).isEqualTo(45);
        assertThat(MetNorwayWeatherClient.wmo("something_new")).isNull();
    }

    @Test
    void refusalsAreLogisticsErrorsAndThereIsNoHistory() {
        stub(429, "{}");
        assertThat(catchThrowable(() -> client().forecast(DOCKS, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 8)).block()))
                .isInstanceOfSatisfying(LogisticsException.class, e -> assertThat(e.kind()).isEqualTo(Kind.RATE_LIMITED));
        assertThat(catchThrowable(() -> client().history(DOCKS, LocalDate.of(2025, 10, 8), LocalDate.of(2025, 10, 8)).block()))
                .isInstanceOfSatisfying(LogisticsException.class, e -> assertThat(e.kind()).isEqualTo(Kind.INVALID_REQUEST));
    }
}
