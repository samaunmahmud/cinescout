package com.cinescout.video;

import com.cinescout.domain.Location;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.resilience.Guard;
import com.cinescout.resilience.GuardFactory;
import com.cinescout.service.NotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Videos of a venue, so the filmmaker can see the place before visiting. The search is for the venue's name
 * and address (or the project's area); the result is cached on the location, because the provider's free
 * quota is small, and searched again once it is older than {@code cacheTtl} or the name or address changed.
 * As elsewhere, database work runs in short transactions that never span the provider call.
 */
public class VideoService {

    private static final TypeReference<List<Video>> VIDEO_LIST = new TypeReference<>() {
    };

    private final VideoSearchClient client;
    private final Guard guard;
    private final LocationRepository locations;
    private final BlockingTransactions db;
    private final ObjectMapper mapper;
    private final VideoProperties props;
    private final Clock clock;

    public VideoService(VideoSearchClient client, GuardFactory guards, LocationRepository locations, BlockingTransactions db,
                        ObjectMapper mapper, VideoProperties props, Clock clock) {
        this.client = client;
        // Only outages trip the breaker; a spent quota or a bad key is not the provider being down.
        this.guard = guards.create("video",
                error -> error instanceof VideoException e && e.isRetryable(),
                error -> error instanceof VideoException e && e.kind() == VideoException.Kind.UNAVAILABLE,
                open -> new VideoException(VideoException.Kind.UNAVAILABLE, "The video circuit breaker is open; the call was not made", open));
        this.locations = locations;
        this.db = db;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
    }

    /**
     * The venue's videos, from the cache when it is fresh.
     *
     * @throws NotFoundException (as an error signal) if the location is not the owner's
     * @throws VideoException    (as an error signal) if a search was needed and failed
     */
    public Mono<LocationVideos> videos(UUID ownerId, UUID locationId) {
        return db.call(() -> lookup(owned(ownerId, locationId)))
                .flatMap(lookup -> lookup.cached() != null
                        ? Mono.just(lookup.cached())
                        : guard.call(() -> client.search(lookup.query(), props.maxResults()))
                                .flatMap(videos -> db.call(() -> save(ownerId, locationId, lookup.query(), videos))));
    }

    private record Lookup(String query, LocationVideos cached) {
    }

    private Lookup lookup(Location location) {
        String query = query(location);
        boolean fresh = location.getVideosJson() != null
                && query.equals(location.getVideosQuery())
                && location.getVideosFetchedAt() != null
                && location.getVideosFetchedAt().plus(props.cacheTtl()).isAfter(clock.instant());
        return new Lookup(query, fresh
                ? new LocationVideos(query, location.getVideosFetchedAt(), read(location.getVideosJson()))
                : null);
    }

    /** The venue's name and where it is: its address, or failing that the project's area. */
    static String query(Location location) {
        String where = location.getAddress() != null && !location.getAddress().isBlank()
                ? location.getAddress()
                : location.getScene().getProject().getLocationArea();
        return where == null || where.isBlank() ? location.getName() : location.getName() + " " + where;
    }

    private LocationVideos save(UUID ownerId, UUID locationId, String query, List<Video> videos) {
        Location location = owned(ownerId, locationId);
        Instant now = clock.instant();
        location.cacheVideos(mapper.valueToTree(videos), query, now);
        locations.saveAndFlush(location);
        return new LocationVideos(query, now, videos);
    }

    private List<Video> read(JsonNode json) {
        return mapper.convertValue(json, VIDEO_LIST).stream().filter(video -> Video.isValidId(video.id())).toList();
    }

    private Location owned(UUID ownerId, UUID locationId) {
        return locations.findOwned(locationId, ownerId).orElseThrow(() -> new NotFoundException("Location", locationId));
    }
}
