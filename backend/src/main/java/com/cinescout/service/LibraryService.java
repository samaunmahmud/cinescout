package com.cinescout.service;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.domain.LibraryVenue;
import com.cinescout.domain.Location;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.dto.LibraryVenueResponse;
import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.TagCountResponse;
import com.cinescout.dto.UpdateLibraryVenueRequest;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LibraryVenueRepository;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A user's own library of venues: saved from any venue they can see, searched and tagged, and copied into any scene
 * they can edit without a new search or AI call. Only its owner ever sees a library venue; anyone else gets 404.
 */
@Service
public class LibraryService {

    /** {@code source_provider} of a location copied in from a library. */
    public static final String LIBRARY_PROVIDER = "library";

    private final LibraryVenueRepository library;
    private final LocationRepository locations;
    private final UserRepository users;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public LibraryService(LibraryVenueRepository library, LocationRepository locations, UserRepository users, ProjectAccess access,
                          BlockingTransactions db) {
        this.library = library;
        this.locations = locations;
        this.users = users;
        this.access = access;
        this.db = db;
    }

    /** Newest first; {@code search} matches the name, address, notes or a tag, {@code tag} keeps one tag. */
    public Mono<PageResponse<LibraryVenueResponse>> list(UUID userId, String search, String tag, PageQuery page) {
        return list(userId, search, tag, null, page);
    }

    /** As {@link #list(UUID, String, String, PageQuery)}, but nearest to {@code near} first when it is given. */
    public Mono<PageResponse<LibraryVenueResponse>> list(UUID userId, String search, String tag, GeoPoint near, PageQuery page) {
        String pattern = search == null || search.isBlank() ? "%" : "%" + likeEscaped(search.strip().toLowerCase(Locale.ROOT)) + "%";
        String wanted = tag == null || tag.isBlank() ? null : tag.strip().toLowerCase(Locale.ROOT);
        if (near == null) {
            return db.call(() -> PageResponse.from(library.search(userId, pattern, wanted, page.pageable()), LibraryVenueResponse::from));
        }
        return db.call(() -> PageResponse.from(library.searchNear(userId, pattern, wanted, near.latitude(), near.longitude(), page.pageable()),
                venue -> LibraryVenueResponse.from(venue, near)));
    }

    public Mono<List<TagCountResponse>> tags(UUID userId) {
        return db.call(() -> library.findTags(userId).stream().map(row -> new TagCountResponse(row.getTag(), row.getVenues())).toList());
    }

    public Mono<LibraryVenueResponse> get(UUID userId, UUID venueId) {
        return db.call(() -> LibraryVenueResponse.from(owned(userId, venueId)));
    }

    /**
     * Keeps a venue the user can see in their library. Saving the same venue again returns the copy already there,
     * with {@code created} false.
     */
    public Mono<Saved> save(UUID userId, UUID locationId) {
        return db.call(() -> {
            Location location = access.location(userId, locationId, ProjectRole.VIEWER);
            return library.findByOwnerIdAndSourceLocationId(userId, locationId)
                    .map(existing -> new Saved(LibraryVenueResponse.from(existing), false))
                    .orElseGet(() -> new Saved(LibraryVenueResponse.from(
                            library.saveAndFlush(LibraryVenue.from(location, users.getReferenceById(userId)))), true));
        });
    }

    /** A saved venue, and whether this call saved it. */
    public record Saved(LibraryVenueResponse venue, boolean created) {
    }

    public Mono<LibraryVenueResponse> update(UUID userId, UUID venueId, UpdateLibraryVenueRequest request) {
        return db.call(() -> {
            LibraryVenue venue = owned(userId, venueId);
            venue.setName(request.name().strip());
            venue.setTags(distinctTags(request.tags()));
            venue.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().strip());
            return LibraryVenueResponse.from(library.saveAndFlush(venue));
        });
    }

    public Mono<Void> delete(UUID userId, UUID venueId) {
        return db.run(() -> library.delete(owned(userId, venueId)));
    }

    /**
     * Copies a library venue into a scene as a venue added without scouting: its place, picture, booking facts and
     * contact; no fit score, and none of the library's private notes.
     *
     * @throws ConflictException (as an error signal) if the scene already has a venue from the same page
     */
    public Mono<LocationResponse> addToScene(UUID userId, UUID sceneId, UUID venueId) {
        return db.call(() -> {
                    Scene scene = access.scene(userId, sceneId, ProjectRole.EDITOR);
                    LibraryVenue venue = owned(userId, venueId);
                    Location location = venue.toLocation(scene);
                    location.setSourceProvider(LIBRARY_PROVIDER);
                    // The picture was looked up when it was scouted; there is nothing more to find.
                    location.setImage(venue.getImageUrl(), DatabaseTime.now());
                    return LocationResponse.from(locations.saveAndFlush(location));
                })
                .onErrorMap(DataIntegrityViolationException.class, Conflicts::translate);
    }

    private LibraryVenue owned(UUID userId, UUID venueId) {
        return library.findByIdAndOwnerId(venueId, userId).orElseThrow(() -> new NotFoundException("Library venue", venueId));
    }

    private static List<String> distinctTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        List<String> kept = new ArrayList<>();
        for (String tag : tags) {
            String clean = tag.strip().replaceAll("\\s+", " ");
            if (!clean.isEmpty() && seen.add(clean.toLowerCase(Locale.ROOT))) {
                kept.add(clean);
            }
        }
        return kept;
    }

    private static String likeEscaped(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
