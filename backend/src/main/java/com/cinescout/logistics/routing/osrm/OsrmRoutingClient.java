package com.cinescout.logistics.routing.osrm;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.routing.Route;
import com.cinescout.logistics.routing.RoutingClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * {@link RoutingClient} backed by OSRM's route service ({@code GET /route/v1/driving/{lon},{lat};{lon},{lat}}),
 * keyless. Durations are OSRM's free-flow estimates: no traffic.
 */
public class OsrmRoutingClient implements RoutingClient {

    private static final String SERVICE = "OSRM";

    private final WebClient osrm;
    private final OsrmProperties props;

    public OsrmRoutingClient(WebClient osrm, OsrmProperties props) {
        this.osrm = osrm;
        this.props = props;
    }

    @Override
    public Mono<Route> drive(GeoPoint from, GeoPoint to) {
        String path = "/route/v1/driving/" + ProviderHttp.coordinate(from.longitude()) + "," + ProviderHttp.coordinate(from.latitude())
                + ";" + ProviderHttp.coordinate(to.longitude()) + "," + ProviderHttp.coordinate(to.latitude());
        Mono<Route> call = osrm.get()
                .uri(uri -> uri.path(path)
                        .queryParam("overview", "false")
                        .queryParam("alternatives", "false")
                        .queryParam("steps", "false")
                        .build())
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    // OSRM answers "no route" and a few other refusals as 400 with a code; the body says which.
                    if (status.is5xxServerError() || status.value() == 429) {
                        return Mono.error(LogisticsException.forStatus(SERVICE, status.value()));
                    }
                    return response.bodyToMono(Answer.class).flatMap(answer -> route(answer, status));
                });
        return ProviderHttp.guardTransport(call, SERVICE, props.timeout());
    }

    private static Mono<Route> route(Answer answer, HttpStatusCode status) {
        if (answer == null || answer.code() == null) {
            return Mono.error(new LogisticsException(Kind.UNAVAILABLE, "OSRM sent an unreadable answer (HTTP " + status.value() + ")"));
        }
        return switch (answer.code()) {
            case "Ok" -> answer.routes() == null || answer.routes().isEmpty() ? Mono.empty()
                    : Mono.just(new Route(answer.routes().getFirst().distance(), answer.routes().getFirst().duration()));
            case "NoRoute", "NoSegment" -> Mono.empty();
            default -> Mono.error(new LogisticsException(Kind.INVALID_REQUEST, "OSRM refused the request: " + answer.code()));
        };
    }

    @Override
    public String attribution() {
        return "Routing by OSRM, map data © OpenStreetMap contributors (ODbL)";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Answer(String code, List<Leg> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Leg(double distance, double duration) {
    }
}
