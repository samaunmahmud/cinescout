package com.cinescout.logistics.places;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FallbackPlacesClientTest {

    private static final GeoPoint AT = new GeoPoint(51.5, 0.0);
    private static final List<Place> SECOND = List.of(new Place(PlaceKind.FOOD, "Cafe", AT, 10));

    private final PlacesClient primary = mock(PlacesClient.class);
    private final PlacesClient second = mock(PlacesClient.class);

    {
        when(second.around(any())).thenReturn(Mono.just(SECOND));
    }

    @Test
    void aQueryTheFirstServerRefusesAtOnceGoesToTheSecond() {
        when(primary.around(any())).thenReturn(Mono.error(new LogisticsException(Kind.UNAVAILABLE, "Connection refused")));
        assertThat(new FallbackPlacesClient(primary, second, Duration.ofSeconds(10)).around(AT).block()).isEqualTo(SECOND);
    }

    @Test
    void aSlowFailureABadRequestOrTwoFailuresKeepTheFirstError() {
        when(primary.around(any())).thenReturn(Mono.delay(Duration.ofMillis(50)).then(Mono.error(new LogisticsException(Kind.UNAVAILABLE, "timed out"))));
        assertThat(catchThrowable(() -> new FallbackPlacesClient(primary, second, Duration.ofMillis(10)).around(AT).block())).hasMessage("timed out");

        when(primary.around(any())).thenReturn(Mono.error(new LogisticsException(Kind.INVALID_REQUEST, "bad query")));
        assertThat(catchThrowable(() -> new FallbackPlacesClient(primary, second, Duration.ofSeconds(10)).around(AT).block())).hasMessage("bad query");
        verify(second, never()).around(any());

        when(primary.around(any())).thenReturn(Mono.error(new LogisticsException(Kind.RATE_LIMITED, "first")));
        when(second.around(any())).thenReturn(Mono.error(new LogisticsException(Kind.UNAVAILABLE, "second")));
        assertThat(catchThrowable(() -> new FallbackPlacesClient(primary, second, Duration.ofSeconds(10)).around(AT).block())).hasMessage("first");
    }
}
