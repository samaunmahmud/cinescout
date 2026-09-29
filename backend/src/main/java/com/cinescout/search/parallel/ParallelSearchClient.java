package com.cinescout.search.parallel;

import com.cinescout.ai.SearchResult;
import com.cinescout.search.LocationSearchClient;
import com.cinescout.search.LocationSearchRequest;
import com.cinescout.search.SearchException;
import com.cinescout.search.SearchException.Kind;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * {@link LocationSearchClient} backed by the Parallel Search API ({@code POST /v1/search}).
 *
 * <p>Results come from the open web, so each hit is validated as a {@link SearchResult}
 * (http(s) URLs only: the URL is stored and later rendered as a link); hits that fail are
 * dropped rather than failing the whole search. No retry happens here: failures are
 * classified in {@link SearchException} and the orchestration layer owns retry and breaking.
 */
public class ParallelSearchClient implements LocationSearchClient {

    private static final Logger log = LoggerFactory.getLogger(ParallelSearchClient.class);

    public static final String PROVIDER = "parallel";

    private static final String SERVICE = "Parallel";
    private static final int MAX_DETAIL_LENGTH = 200;
    private static final int MAX_EXCERPT_CHARS = 5000;

    private final WebClient parallel;
    private final ParallelProperties props;
    private final ObjectMapper mapper;
    private final Validator validator;

    /** @param parallel a client whose base URL is the Parallel API host */
    public ParallelSearchClient(WebClient parallel, ParallelProperties props, ObjectMapper mapper, Validator validator) {
        this.parallel = parallel;
        this.props = props;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Override
    public Mono<List<SearchResult>> search(LocationSearchRequest request) {
        return Mono.defer(() -> {
                    if (request == null) {
                        return Mono.error(new IllegalArgumentException("request must not be null"));
                    }
                    return post(requestBody(request));
                })
                .transform(call -> guarded(call, request == null ? 0 : request.maxResults()));
    }

    @Override
    public Mono<List<SearchResult>> findVenue(String name, String area, int maxResults) {
        return Mono.defer(() -> {
                    if (name == null || name.isBlank() || area == null || area.isBlank()) {
                        return Mono.error(new IllegalArgumentException("name and area must not be blank"));
                    }
                    if (maxResults < 1 || maxResults > LocationSearchRequest.MAX_RESULTS) {
                        return Mono.error(new IllegalArgumentException("maxResults must be between 1 and " + LocationSearchRequest.MAX_RESULTS));
                    }
                    return post(new SearchBody(ParallelQueryBuilder.venueObjective(name, area),
                            ParallelQueryBuilder.venueQueries(name, area), mode(),
                            new AdvancedSettings(maxResults, new ExcerptSettings(props.excerptChars()))));
                })
                .transform(call -> guarded(call, maxResults));
    }

    /** Timeouts and transport failures classified as {@link SearchException}, and the answer mapped to results. */
    private Mono<List<SearchResult>> guarded(Mono<SearchResponse> call, int maxResults) {
        return call
                .timeout(props.timeout())
                .onErrorMap(TimeoutException.class,
                        e -> new SearchException(Kind.UNAVAILABLE, SERVICE + " did not answer within " + props.timeout(), e))
                .onErrorMap(e -> e instanceof WebClientRequestException
                                || e instanceof DecodingException
                                || e instanceof UnsupportedMediaTypeException,
                        e -> new SearchException(Kind.UNAVAILABLE, SERVICE + " is unreachable or returned an unreadable response", e))
                .map(response -> toResults(response, maxResults));
    }

    private SearchBody requestBody(LocationSearchRequest request) {
        return new SearchBody(
                ParallelQueryBuilder.objective(request),
                ParallelQueryBuilder.queries(request),
                mode(),
                new AdvancedSettings(request.maxResults(), new ExcerptSettings(props.excerptChars())));
    }

    private String mode() {
        return props.mode() == null || props.mode().isBlank() ? null : props.mode();
    }

    private Mono<SearchResponse> post(SearchBody body) {
        return parallel.post()
                .uri("/v1/search")
                .header("x-api-key", props.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toException)
                .bodyToMono(SearchResponse.class)
                .switchIfEmpty(Mono.error(() -> new SearchException(Kind.UNAVAILABLE, SERVICE + " returned an empty response")));
    }

    private Mono<Throwable> toException(ClientResponse response) {
        int status = response.statusCode().value();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(raw -> {
                    SearchException error = SearchException.forStatus(SERVICE, status, null);
                    if (error.kind() == Kind.INVALID_REQUEST) {
                        // Only for our own malformed requests: Parallel's message and ref_id help diagnose them.
                        error = SearchException.forStatus(SERVICE, status, errorDetail(raw));
                    }
                    return error;
                });
    }

    /** Parallel's error message and reference id, if the body has them; never the raw body. */
    private String errorDetail(String raw) {
        try {
            JsonNode error = mapper.readTree(raw).path("error");
            String message = error.path("message").asText("");
            String refId = error.path("ref_id").asText("");
            String detail = (message + (refId.isEmpty() ? "" : " (ref " + refId + ")")).strip().replaceAll("\\s+", " ");
            return detail.length() > MAX_DETAIL_LENGTH ? detail.substring(0, MAX_DETAIL_LENGTH) + "..." : detail;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<SearchResult> toResults(SearchResponse response, int maxResults) {
        if (response.warnings() != null && !response.warnings().isEmpty()) {
            log.warn("{} adjusted the search request: {}", SERVICE, response.warnings().stream()
                    .map(w -> String.valueOf(w.type())).collect(Collectors.joining(", ")));
        }
        if (response.results() == null) {
            return List.of();
        }

        // Keyed by URL so a page returned twice appears once, first (best-ranked) occurrence kept.
        Map<String, SearchResult> byUrl = new LinkedHashMap<>();
        int dropped = 0;
        for (Hit hit : response.results()) {
            SearchResult result = toResult(hit);
            if (result == null) {
                dropped++;
            } else {
                byUrl.putIfAbsent(result.url(), result);
            }
        }
        if (dropped > 0) {
            log.debug("{} returned {} unusable result(s), skipped", SERVICE, dropped);
        }
        return byUrl.values().stream().limit(maxResults).toList();
    }

    /** The hit as a validated {@link SearchResult}, or null if it is not usable. */
    private SearchResult toResult(Hit hit) {
        if (hit == null || hit.url() == null) {
            return null;
        }
        String url = hit.url().strip();
        String title = hit.title() == null ? "" : hit.title().strip();
        if (title.isEmpty()) {
            title = hostOf(url);
        }
        String excerpt = hit.excerpts() == null ? null : hit.excerpts().stream()
                .filter(e -> e != null && !e.isBlank())
                .map(String::strip)
                .collect(Collectors.joining("\n\n"));
        if (excerpt != null && excerpt.isEmpty()) {
            excerpt = null;
        } else if (excerpt != null && excerpt.length() > MAX_EXCERPT_CHARS) {
            excerpt = excerpt.substring(0, MAX_EXCERPT_CHARS);
        }

        SearchResult result = new SearchResult(title, url, excerpt, PROVIDER);
        return validator.validate(result).isEmpty() ? result : null;
    }

    /** A readable stand-in title for pages that have none; blank when the URL has no host. */
    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    // --- wire format ---------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SearchBody(String objective,
                      @JsonProperty("search_queries") List<String> searchQueries,
                      String mode,
                      @JsonProperty("advanced_settings") AdvancedSettings advancedSettings) {
    }

    record AdvancedSettings(@JsonProperty("max_results") int maxResults,
                            @JsonProperty("excerpt_settings") ExcerptSettings excerptSettings) {
    }

    record ExcerptSettings(@JsonProperty("max_chars_per_result") int maxCharsPerResult) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<Hit> results, List<Warning> warnings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Hit(String url, String title, List<String> excerpts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Warning(String type) {
    }
}
