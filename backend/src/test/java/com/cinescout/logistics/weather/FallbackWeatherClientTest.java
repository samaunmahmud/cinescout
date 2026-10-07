package com.cinescout.logistics.weather;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FallbackWeatherClientTest {

    private static final GeoPoint AT = new GeoPoint(51.5, 0.0);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 12);
    private static final WeatherSeries PRIMARY = new WeatherSeries(ZoneId.of("Europe/London"), List.of());
    private static final WeatherSeries SECOND = new WeatherSeries(null, List.of());

    private final WeatherClient primary = mock(WeatherClient.class);
    private final WeatherClient second = mock(WeatherClient.class);
    private final FallbackWeatherClient client = new FallbackWeatherClient(primary, second);

    {
        when(primary.attribution()).thenReturn("Primary");
        when(second.attribution()).thenReturn("Second");
        when(second.forecast(any(), any(), any())).thenReturn(Mono.just(SECOND));
    }

    @Test
    void thePrimaryAnswersWhenItCan() {
        when(primary.forecast(any(), any(), any())).thenReturn(Mono.just(PRIMARY));
        assertThat(client.forecast(AT, DAY, DAY).block()).isSameAs(PRIMARY);
        verify(second, never()).forecast(any(), any(), any());
    }

    @Test
    void aRateLimitedOrDownPrimaryHandsTheForecastOverCreditingTheSecond() {
        for (Kind kind : List.of(Kind.RATE_LIMITED, Kind.UNAVAILABLE)) {
            when(primary.forecast(any(), any(), any())).thenReturn(Mono.error(new LogisticsException(kind, "no")));
            WeatherSeries series = client.forecast(AT, DAY, DAY).block();
            assertThat(series.zone()).isNull();
            assertThat(series.attribution()).isEqualTo("Second");
        }
        assertThat(client.attribution()).isEqualTo("Primary");
    }

    @Test
    void aBadRequestIsNotRetriedElsewhereAndWhenBothFailThePrimarysErrorStands() {
        when(primary.forecast(any(), any(), any())).thenReturn(Mono.error(new LogisticsException(Kind.INVALID_REQUEST, "bad")));
        assertThat(catchThrowable(() -> client.forecast(AT, DAY, DAY).block())).hasMessage("bad");
        verify(second, never()).forecast(any(), any(), any());

        when(primary.forecast(any(), any(), any())).thenReturn(Mono.error(new LogisticsException(Kind.RATE_LIMITED, "first")));
        when(second.forecast(any(), any(), any())).thenReturn(Mono.error(new LogisticsException(Kind.UNAVAILABLE, "second")));
        assertThat(catchThrowable(() -> client.forecast(AT, DAY, DAY).block())).hasMessage("first");
    }
}
