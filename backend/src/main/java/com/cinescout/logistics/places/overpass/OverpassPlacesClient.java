package com.cinescout.logistics.places.overpass;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.places.Place;
import com.cinescout.logistics.places.PlaceKind;
import com.cinescout.logistics.places.PlacesClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@link PlacesClient} backed by OpenStreetMap through the Overpass API ({@code POST /api/interpreter}),
 * keyless. One query fetches every {@link PlaceKind} within its radius; what comes back is classified by
 * the same {@link OsmRules} that built the query and measured from the location.
 *
 * <p>Overpass reports a query it could not finish (too busy, out of time) as HTTP 200 with a
 * {@code remark}; that is treated as unavailable rather than as "nothing nearby".
 */
public class OverpassPlacesClient implements PlacesClient {

    /** A failure this soon after asking is the server turning the query away, not failing to answer it. */
    private static final Duration TURNED_AWAY_WITHIN = Duration.ofSeconds(3);

    private static final String SERVICE = "Overpass";

    private final WebClient overpass;
    private final OverpassProperties props;

    /** @param overpass a client whose base URL is the Overpass host */
    public OverpassPlacesClient(WebClient overpass, OverpassProperties props) {
        this.overpass = overpass;
        this.props = props;
    }

    /**
     * A busy public server fails in two ways. It may take the query and time out after half a minute: trying
     * again would hold the request for another half minute and add to the load, so that is final. Or it turns
     * the query away at once (a 504 or 429 within a second or two), and a moment later often takes it: that is
     * worth one more try after a short pause.
     */
    @Override
    public Mono<List<Place>> around(GeoPoint point) {
        return Mono.defer(() -> {
            long started = System.nanoTime();
            return query(point).onErrorResume(LogisticsException.class, error -> {
                boolean turnedAway = error.isRetryable() && Duration.ofNanos(System.nanoTime() - started).compareTo(TURNED_AWAY_WITHIN) < 0;
                return turnedAway ? Mono.delay(props.retryPause()).then(query(point)) : Mono.error(error);
            });
        });
    }

    private Mono<List<Place>> query(GeoPoint point) {
        Mono<List<Place>> call = Mono.defer(() -> overpass.post()
                        .uri("/api/interpreter")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData("data", OverpassQuery.around(point, props.serverTimeoutSeconds())))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError,
                                response -> Mono.just(LogisticsException.forStatus(SERVICE, response.statusCode().value())))
                        .bodyToMono(Response.class))
                .switchIfEmpty(Mono.error(() -> new LogisticsException(Kind.UNAVAILABLE, SERVICE + " returned an empty response")))
                .map(response -> toPlaces(response, point));
        return ProviderHttp.guardTransport(call, SERVICE, props.timeout());
    }

    @Override
    public String attribution() {
        return "Map data © OpenStreetMap contributors (ODbL)";
    }

    private static List<Place> toPlaces(Response response, GeoPoint origin) {
        if (response.remark() != null && response.remark().toLowerCase(Locale.ROOT).contains("error")) {
            // e.g. "runtime error: Query timed out in "query" at line 3 after 21 seconds."
            throw new LogisticsException(Kind.UNAVAILABLE, SERVICE + " could not finish the query: " + response.remark());
        }
        List<Place> places = new ArrayList<>();
        if (response.elements() == null) {
            return places;
        }
        for (Element element : response.elements()) {
            List<PlaceKind> kinds = OsmRules.classify(element.tags());
            for (PlaceKind kind : kinds) {
                Place place = toPlace(element, kind, origin);
                // Overpass measured the radius to the element's outline, so an element of one kind is in range
                // even if its centre is not (a large airport). One that is several kinds may have been found by
                // the widest of their radii, so it only counts as the kinds whose radius its centre is within.
                if (place != null && (kinds.size() == 1 || place.distanceMeters() <= kind.radiusMeters())) {
                    places.add(place);
                }
            }
        }
        return places;
    }

    /** The element as a place of {@code kind}, or null if it has no usable position. */
    private static Place toPlace(Element element, PlaceKind kind, GeoPoint origin) {
        String name = nameOf(element.tags());
        if (element.geometry() != null && !element.geometry().isEmpty()) {
            Double distance = distanceToLine(element.geometry(), origin);
            return distance == null ? null : new Place(kind, name, null, distance);
        }
        GeoPoint position = element.lat() != null && element.lon() != null
                ? point(element.lat(), element.lon())
                : element.center() == null ? null : point(element.center().lat(), element.center().lon());
        return position == null ? null : new Place(kind, name, position, origin.distanceTo(position));
    }

    /** The distance to the nearest stretch of a line; null if it has no valid vertex at all. */
    private static Double distanceToLine(List<LatLon> geometry, GeoPoint origin) {
        Double nearest = null;
        GeoPoint previous = null;
        for (LatLon vertex : geometry) {
            GeoPoint current = vertex == null ? null : point(vertex.lat(), vertex.lon());
            if (current == null) {
                previous = null; // a gap in the line: do not bridge it
                continue;
            }
            double distance = previous == null ? origin.distanceTo(current) : origin.distanceToSegment(previous, current);
            nearest = nearest == null ? distance : Math.min(nearest, distance);
            previous = current;
        }
        return nearest;
    }

    private static GeoPoint point(Double lat, Double lon) {
        if (lat == null || lon == null) {
            return null;
        }
        try {
            return new GeoPoint(lat, lon);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The map's name, else its reference (a road number such as "I 278"), else null. */
    private static String nameOf(Map<String, String> tags) {
        String name = tags.get("name");
        if (name != null && !name.isBlank()) {
            return name.strip();
        }
        String ref = tags.get("ref");
        return ref == null || ref.isBlank() ? null : ref.strip();
    }

    // --- wire format ---------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Element> elements, String remark) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Element(String type, Double lat, Double lon, LatLon center, List<LatLon> geometry, Map<String, String> tags) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LatLon(Double lat, Double lon) {
    }
}
