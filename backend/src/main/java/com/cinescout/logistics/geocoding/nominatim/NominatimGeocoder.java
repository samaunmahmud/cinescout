package com.cinescout.logistics.geocoding.nominatim;

import com.cinescout.domain.AdminArea;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.geocoding.Geocoder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

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

    /**
     * {@code GET /reverse} at zoom 10, the level of a city's districts: for London that is the borough, with its
     * ISO 3166-2 code (e.g. GB-CMD) among the address parts.
     */
    @Override
    public Mono<AdminArea> areaAt(GeoPoint point) {
        Mono<AdminArea> call = nominatim.get()
                .uri(uri -> uri.path("/reverse")
                        .queryParam("lat", point.latitude())
                        .queryParam("lon", point.longitude())
                        .queryParam("format", "jsonv2")
                        .queryParam("zoom", 10)
                        .queryParam("addressdetails", 1)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        response -> Mono.just(LogisticsException.forStatus(SERVICE, response.statusCode().value())))
                .bodyToMono(Place.class)
                .flatMap(place -> Mono.justOrEmpty(area(place, point)));
        return ProviderHttp.guardTransport(call, SERVICE, props.timeout());
    }

    /** The most local named area and every ISO 3166-2 code given, most local (highest level) first. */
    static AdminArea area(Place place, GeoPoint point) {
        if (place == null || place.address() == null || place.address().isEmpty()) {
            return null;
        }
        Map<String, String> address = place.address();
        String name = Stream.of("city_district", "borough", "city", "county", "state_district", "state")
                .map(address::get).filter(value -> value != null && !value.isBlank()).findFirst().orElse(place.name());
        List<String> codes = address.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("ISO3166-2-lvl"))
                .sorted(Comparator.comparing((Map.Entry<String, String> entry) -> level(entry.getKey())).reversed())
                .map(Map.Entry::getValue)
                .toList();
        String country = address.get("country_code");
        return new AdminArea(name, codes, country == null ? null : country.toLowerCase(Locale.ROOT), point.latitude(), point.longitude());
    }

    private static int level(String key) {
        try {
            return Integer.parseInt(key.substring("ISO3166-2-lvl".length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** {@code GET /reverse} at zoom 14, the level of neighbourhoods and small towns. */
    @Override
    public Mono<String> placeAt(GeoPoint point) {
        Mono<String> call = nominatim.get()
                .uri(uri -> uri.path("/reverse")
                        .queryParam("lat", point.latitude())
                        .queryParam("lon", point.longitude())
                        .queryParam("format", "jsonv2")
                        .queryParam("zoom", 14)
                        .queryParam("addressdetails", 1)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        response -> Mono.just(LogisticsException.forStatus(SERVICE, response.statusCode().value())))
                .bodyToMono(Place.class)
                .flatMap(place -> Mono.justOrEmpty(placeName(place)));
        return ProviderHttp.guardTransport(call, SERVICE, props.timeout());
    }

    /** The neighbourhood, the town or city, and the country, each once: "Shoreditch, London, United Kingdom". */
    static String placeName(Place place) {
        if (place == null || place.address() == null || place.address().isEmpty()) {
            return null;
        }
        Map<String, String> address = place.address();
        List<String> parts = Stream.of(
                        first(address, "suburb", "neighbourhood", "quarter", "city_district", "borough"),
                        first(address, "city", "town", "village", "municipality", "county"),
                        first(address, "country"))
                .filter(part -> part != null)
                .distinct()
                .toList();
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String first(Map<String, String> address, String... keys) {
        return Stream.of(keys).map(address::get).filter(value -> value != null && !value.isBlank()).map(String::strip)
                .findFirst().orElse(null);
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Place(String name, Map<String, String> address) {
    }
}
