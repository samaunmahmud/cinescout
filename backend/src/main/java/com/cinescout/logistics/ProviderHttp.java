package com.cinescout.logistics;

import com.cinescout.logistics.LogisticsException.Kind;
import io.netty.channel.ChannelOption;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/** What the logistics provider clients share: how their WebClients are built and how transport failures are classified. */
public final class ProviderHttp {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    /** Overpass answers for a dense city centre run to a few hundred kilobytes, above WebClient's 256 KB default. */
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    private ProviderHttp() {
    }

    /**
     * A client for one provider. The free OpenStreetMap services require a User-Agent that identifies the
     * application (their usage policies block generic ones), so every request carries {@code userAgent}.
     */
    public static WebClient webClient(WebClient.Builder builder, String baseUrl, Duration timeout, String userAgent) {
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(timeout);
        return builder.clone()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
    }

    /** A coordinate as plain decimal text (never {@code 1.0E-4}), to the six places the database keeps. */
    public static String coordinate(double degrees) {
        return String.format(Locale.ROOT, "%.6f", degrees);
    }

    /**
     * Bounds {@code call} by {@code timeout} and turns transport failures (timeouts, refused connections,
     * unreadable or oversized bodies) into {@link LogisticsException}s of kind {@code UNAVAILABLE}.
     */
    public static <T> Mono<T> guardTransport(Mono<T> call, String service, Duration timeout) {
        return call
                .timeout(timeout)
                .onErrorMap(TimeoutException.class,
                        e -> new LogisticsException(Kind.UNAVAILABLE, service + " did not answer within " + timeout, e))
                .onErrorMap(e -> e instanceof WebClientRequestException
                                || e instanceof DecodingException
                                || e instanceof UnsupportedMediaTypeException
                                || e instanceof DataBufferLimitException
                                || e instanceof WebClientResponseException,
                        e -> new LogisticsException(Kind.UNAVAILABLE, service + " is unreachable or returned an unreadable response", e));
    }
}
