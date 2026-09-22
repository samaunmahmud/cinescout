package com.cinescout.logistics.weather.openmeteo;

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

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Response bodies are trimmed copies of real Open-Meteo answers (captured 2026-09-22). */
class OpenMeteoWeatherClientTest {

    private static final GeoPoint NEW_YORK = new GeoPoint(40.7128, -74.006);
    private static final LocalDate FROM = LocalDate.of(2026, 9, 23);
    private static final LocalDate TO = LocalDate.of(2026, 9, 24);

    private static final String FORECAST = """
            {"latitude":40.710335,"longitude":-73.99308,"utc_offset_seconds":-14400,"timezone":"America/New_York",
             "daily_units":{"time":"iso8601","weather_code":"wmo code"},
             "daily":{"time":["2026-09-23","2026-09-24"],"weather_code":[3,51],"temperature_2m_max":[18.4,19.8],
                      "temperature_2m_min":[12.6,11.5],"precipitation_sum":[0.00,0.40],"precipitation_probability_max":[0,4],
                      "wind_speed_10m_max":[28.8,27.9],"wind_gusts_10m_max":[48.2,44.5],"cloud_cover_mean":[92,75]}}""";

    private static final String ARCHIVE = """
            {"latitude":40.738136,"longitude":-74.04254,"timezone":"America/New_York",
             "daily":{"time":["2025-12-01","2025-12-02"],"weather_code":[3,71],"temperature_2m_max":[6.1,3.0],
                      "temperature_2m_min":[-0.4,-1.6],"precipitation_sum":[0.00,17.20],"wind_speed_10m_max":[17.8,17.2],
                      "wind_gusts_10m_max":[39.2,35.6],"cloud_cover_mean":[53,100]}}""";

    @RegisterExtension
    static WireMockExtension api = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private OpenMeteoWeatherClient client() {
        return client(api.baseUrl(), Duration.ofSeconds(5));
    }

    private OpenMeteoWeatherClient client(String baseUrl, Duration timeout) {
        OpenMeteoProperties props = new OpenMeteoProperties(baseUrl, baseUrl, 15, 30, timeout);
        return new OpenMeteoWeatherClient(
                ProviderHttp.webClient(WebClient.builder(), baseUrl, timeout, "CineScout-test"),
                ProviderHttp.webClient(WebClient.builder(), baseUrl, timeout, "CineScout-test"), props);
    }

    private void stub(String path, int status, String body) {
        api.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    void aForecastIsReadDayByDayWithTheLocationsTimeZone() {
        stub("/v1/forecast", 200, FORECAST);

        WeatherSeries series = client().forecast(NEW_YORK, FROM, TO).block();

        assertThat(series.zone()).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(series.days()).containsExactly(
                new DailyWeather(FROM, 3, 18.4, 12.6, 0.0, 0, 28.8, 48.2, 92),
                new DailyWeather(TO, 51, 19.8, 11.5, 0.4, 4, 27.9, 44.5, 75));
    }

    @Test
    void theForecastRequestAsksForTheDatesTheVariablesAndTheLocalTimeZone() {
        stub("/v1/forecast", 200, FORECAST);

        client().forecast(new GeoPoint(0.0001, -74.006), FROM, TO).block();

        api.verify(getRequestedFor(urlPathEqualTo("/v1/forecast"))
                .withQueryParam("latitude", equalTo("0.000100")) // plain decimals, never 1.0E-4
                .withQueryParam("longitude", equalTo("-74.006000"))
                .withQueryParam("start_date", equalTo("2026-09-23"))
                .withQueryParam("end_date", equalTo("2026-09-24"))
                .withQueryParam("timezone", equalTo("auto"))
                .withQueryParam("daily", equalTo("weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,"
                        + "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,cloud_cover_mean"))
                .withHeader("User-Agent", equalTo("CineScout-test")));
    }

    @Test
    void historyComesFromTheArchiveWithoutAPrecipitationProbability() {
        stub("/v1/archive", 200, ARCHIVE);

        WeatherSeries series = client().history(NEW_YORK, LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 2)).block();

        assertThat(series.days().getLast()).isEqualTo(
                new DailyWeather(LocalDate.of(2025, 12, 2), 71, 3.0, -1.6, 17.2, null, 17.2, 35.6, 100));
        api.verify(getRequestedFor(urlPathEqualTo("/v1/archive"))
                .withQueryParam("daily", equalTo("weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,"
                        + "wind_speed_10m_max,wind_gusts_10m_max,cloud_cover_mean")));
    }

    @Test
    void missingValuesAndColumnsAreNullNotErrors() {
        stub("/v1/forecast", 200, """
                {"timezone":"Europe/Oslo","daily":{"time":["2026-09-23","2026-09-24"],"weather_code":[null,2],"temperature_2m_max":[5.0]}}""");

        WeatherSeries series = client().forecast(NEW_YORK, FROM, TO).block();

        assertThat(series.days()).containsExactly(
                new DailyWeather(FROM, null, 5.0, null, null, null, null, null, null),
                new DailyWeather(TO, 2, null, null, null, null, null, null, null));
    }

    @Test
    void anUnknownTimeZoneFallsBackToUtc() {
        stub("/v1/forecast", 200, """
                {"timezone":"Mars/Olympus_Mons","daily":{"time":["2026-09-23"]}}""");

        assertThat(client().forecast(NEW_YORK, FROM, FROM).block().zone()).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    void aRejectedRequestKeepsOpenMeteosReason() {
        stub("/v1/forecast", 400, """
                {"error":true,"reason":"Parameter 'start_date' is out of allowed range from 2026-06-21 to 2026-10-07"}""");

        Throwable error = catchThrowable(() -> client().forecast(NEW_YORK, FROM, TO).block());

        assertThat(error).isInstanceOf(LogisticsException.class)
                .hasMessage("Open-Meteo returned HTTP 400: Parameter 'start_date' is out of allowed range from 2026-06-21 to 2026-10-07");
        assertThat(((LogisticsException) error).kind()).isEqualTo(Kind.INVALID_REQUEST);
    }

    @Test
    void rateLimitsAndServerErrorsAreRetryable() {
        stub("/v1/forecast", 429, "{\"error\":true,\"reason\":\"Daily API request limit exceeded\"}");
        LogisticsException limited = (LogisticsException) catchThrowable(() -> client().forecast(NEW_YORK, FROM, TO).block());
        assertThat(limited.kind()).isEqualTo(Kind.RATE_LIMITED);
        assertThat(limited.isRetryable()).isTrue();

        stub("/v1/forecast", 502, "<html>Bad gateway</html>");
        LogisticsException down = (LogisticsException) catchThrowable(() -> client().forecast(NEW_YORK, FROM, TO).block());
        assertThat(down.kind()).isEqualTo(Kind.UNAVAILABLE);
        assertThat(down).hasMessage("Open-Meteo returned HTTP 502");
    }

    @Test
    void aResponseWithoutDailyValuesIsUnavailable() {
        stub("/v1/forecast", 200, "{\"timezone\":\"UTC\"}");

        Throwable error = catchThrowable(() -> client().forecast(NEW_YORK, FROM, TO).block());

        assertThat(error).isInstanceOf(LogisticsException.class).hasMessageContaining("no daily values");
    }

    @Test
    void garbageIsUnavailable() {
        api.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody("{not json")));

        assertThat(((LogisticsException) catchThrowable(() -> client().forecast(NEW_YORK, FROM, TO).block())).kind())
                .isEqualTo(Kind.UNAVAILABLE);
    }

    @Test
    void aSlowAnswerTimesOutAsUnavailable() {
        api.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(aResponse()
                .withFixedDelay(2_000).withStatus(200).withHeader("Content-Type", "application/json").withBody(FORECAST)));

        Throwable error = catchThrowable(() -> client(api.baseUrl(), Duration.ofMillis(300)).forecast(NEW_YORK, FROM, TO).block());

        assertThat(error).isInstanceOf(LogisticsException.class);
        assertThat(((LogisticsException) error).kind()).isEqualTo(Kind.UNAVAILABLE);
    }

    @Test
    void anUnreachableHostIsUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        Throwable error = catchThrowable(() -> client("http://localhost:" + closedPort, Duration.ofSeconds(5))
                .forecast(NEW_YORK, FROM, TO).block());

        assertThat(((LogisticsException) error).kind()).isEqualTo(Kind.UNAVAILABLE);
    }
}
