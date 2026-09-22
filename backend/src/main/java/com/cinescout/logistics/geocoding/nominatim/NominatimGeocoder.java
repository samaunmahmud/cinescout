package com.cinescout.logistics.geocoding.nominatim;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.geocoding.Geocoder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * {@link Geocoder} backed by Nominatim, OpenStreetMap's geocoder ({@code GET /search}), keyless. Only the
 * best match is asked for; Nominatim sends coordinates as strings, and a match whose coordinates do not
 * parse is treated as no match.
 */
public class NominatimGeocoder implements Geocoder {

    private static final String SERVICE = "Nominatim";

    private final WebClient nominatim;
    private final NominatimProperties props;

    /** @param nominatim a client whose base URL is the Nominatim host */
    public NominatimGeocoder(WebClient nominatim, NominatimProperties props) {
        this.nominatim = nominatim;
        this.props = props;
    }

    @Override
    public Mono<GeoPoint> locate(String query) {
        if (query == null || query.isBlank()) {
            return Mono.empty();
        }
        Mono<GeoPoint> call = nominatim.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("q", "{q}")
                        .queryParam("format", "jsonv2")
                        .queryParam("limit", 1)
                        .build(query.strip()))
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        response -> Mono.just(LogisticsException.forStatus(SERVICE, response.statusCode().value())))
                .bodyToMono(Match[].class)
                .flatMap(matches -> Mono.justOrEmpty(best(List.of(matches))));
        return ProviderHttp.guardTransport(call, SERVICE, props.timeout());
    }

    @Override
    public String attribution() {
        return "Geocoding by Nominatim, map data © OpenStreetMap contributors (ODbL)";
    }

    private static GeoPoint best(List<Match> matches) {
        if (matches.isEmpty() || matches.getFirst() == null) {
            return null;
        }
        Match match = matches.getFirst();
        if (match.lat() == null || match.lon() == null) {
            return null;
        }
        try {
            return new GeoPoint(Double.parseDouble(match.lat()), Double.parseDouble(match.lon()));
        } catch (IllegalArgumentException e) { // unparseable (NumberFormatException) or out of range
            return null;
        }
    }

    // --- wire format ---------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Match(String lat, String lon) {
    }
}
