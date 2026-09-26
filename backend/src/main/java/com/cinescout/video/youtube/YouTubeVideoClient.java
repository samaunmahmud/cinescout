package com.cinescout.video.youtube;

import com.cinescout.video.Video;
import com.cinescout.video.VideoException;
import com.cinescout.video.VideoException.Kind;
import com.cinescout.video.VideoSearchClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * {@link VideoSearchClient} backed by the YouTube Data API v3 ({@code GET /youtube/v3/search}). Only videos
 * that may be embedded are asked for, with strict safe search. The key travels in the {@code X-Goog-Api-Key}
 * header, so it never appears in a URL or an access log. Titles come HTML-escaped and are unescaped here.
 */
public class YouTubeVideoClient implements VideoSearchClient {

    private static final String SERVICE = "YouTube";

    private final WebClient youtube;
    private final YouTubeProperties props;
    private final ObjectMapper mapper;

    /** @param youtube a client whose base URL is the Google APIs host */
    public YouTubeVideoClient(WebClient youtube, YouTubeProperties props, ObjectMapper mapper) {
        this.youtube = youtube;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public Mono<List<Video>> search(String query, int maxResults) {
        return youtube.get()
                .uri(uri -> uri.path("/youtube/v3/search")
                        .queryParam("part", "snippet")
                        .queryParam("type", "video")
                        .queryParam("videoEmbeddable", "true")
                        .queryParam("safeSearch", "strict")
                        .queryParam("maxResults", maxResults)
                        .queryParam("q", "{q}")
                        .build(query))
                .header("X-Goog-Api-Key", props.apiKey())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toException)
                .bodyToMono(SearchResponse.class)
                .map(YouTubeVideoClient::toVideos)
                .timeout(props.timeout())
                .onErrorMap(TimeoutException.class,
                        e -> new VideoException(Kind.UNAVAILABLE, SERVICE + " did not answer within " + props.timeout(), e))
                .onErrorMap(e -> e instanceof WebClientRequestException || e instanceof DecodingException
                                || e instanceof UnsupportedMediaTypeException,
                        e -> new VideoException(Kind.UNAVAILABLE, SERVICE + " is unreachable or returned an unreadable response", e));
    }

    private Mono<Throwable> toException(ClientResponse response) {
        int status = response.statusCode().value();
        return response.bodyToMono(String.class).defaultIfEmpty("").map(body -> classify(status, body));
    }

    /** Google reports the cause in {@code error.errors[].reason} (and for a bad key, in the message). */
    VideoException classify(int status, String body) {
        String reason = "";
        String message = "";
        try {
            JsonNode error = mapper.readTree(body).path("error");
            reason = error.path("errors").path(0).path("reason").asText("");
            message = error.path("message").asText("");
        } catch (Exception e) {
            // Not JSON: go by the status alone.
        }
        String summary = SERVICE + " returned HTTP " + status + (reason.isEmpty() ? "" : " (" + reason + ")");
        Kind kind = switch (reason) {
            case "quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded", "userRateLimitExceeded" -> Kind.QUOTA_EXCEEDED;
            case "keyInvalid", "forbidden", "accessNotConfigured", "ipRefererBlocked", "keyExpired" -> Kind.AUTHENTICATION;
            default -> {
                if (message.contains("API key")) yield Kind.AUTHENTICATION;
                if (status == 429) yield Kind.QUOTA_EXCEEDED;
                if (status == 401 || status == 403) yield Kind.AUTHENTICATION;
                yield status >= 500 || status == 408 ? Kind.UNAVAILABLE : Kind.INVALID_REQUEST;
            }
        };
        return new VideoException(kind, summary);
    }

    private static List<Video> toVideos(SearchResponse response) {
        if (response.items() == null) {
            return List.of();
        }
        return response.items().stream()
                .filter(Objects::nonNull)
                .map(YouTubeVideoClient::toVideo)
                .filter(Objects::nonNull)
                .toList();
    }

    /** A hit without a well-formed id is dropped: the id is what links are built from. */
    private static Video toVideo(Item item) {
        String id = item.id() == null ? null : item.id().videoId();
        if (!Video.isValidId(id) || item.snippet() == null) {
            return null;
        }
        Snippet snippet = item.snippet();
        return new Video(id, text(snippet.title()), text(snippet.channelTitle()), instant(snippet.publishedAt()));
    }

    private static String text(String escaped) {
        return escaped == null ? "" : HtmlUtils.htmlUnescape(escaped).strip();
    }

    private static Instant instant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // --- wire format ---------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(ItemId id, Snippet snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemId(String videoId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Snippet(String title, String channelTitle, String publishedAt) {
    }
}
