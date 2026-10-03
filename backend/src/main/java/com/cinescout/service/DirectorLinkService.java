package com.cinescout.service;

import com.cinescout.domain.ActivityTarget;
import com.cinescout.domain.ActivityVerb;
import com.cinescout.domain.DirectorLink;
import com.cinescout.domain.DirectorResponse;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.domain.VenueComment;
import com.cinescout.dto.DirectorLinkRequest;
import com.cinescout.dto.DirectorLinkResponse;
import com.cinescout.dto.DirectorResponseRequest;
import com.cinescout.dto.DirectorResponseResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.PublicShortlistResponse;
import com.cinescout.dto.ShortlistVenueResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.DirectorLinkRepository;
import com.cinescout.repository.DirectorResponseRepository;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.UserRepository;
import com.cinescout.repository.VenueCommentRepository;
import com.cinescout.security.SecretTokens;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Director links: a page without a login that shows a project's (or one scene's) shortlisted venues to someone who
 * decides, usually the director, and takes their Approve, Maybe or No on each. Modelled on the call sheet link: a
 * random token, one link per project and per scene, replaced by making a new one and withdrawn by deleting it.
 * Private notes, quotes and the reasons behind fit scores are shown only when the owner made the link to show them.
 */
@Service
public class DirectorLinkService {

    /** What a director link shows: venues on the shortlist, and those that went on from it. */
    static final Set<LocationStatus> SHOWN = EnumSet.of(LocationStatus.SHORTLISTED, LocationStatus.CONTACTED, LocationStatus.CONFIRMED);

    private final DirectorLinkRepository links;
    private final DirectorResponseRepository responses;
    private final VenueCommentRepository comments;
    private final LocationRepository locations;
    private final UserRepository users;
    private final ProjectAccess access;
    private final BlockingTransactions db;
    private final ActivityLog activity;

    public DirectorLinkService(DirectorLinkRepository links, DirectorResponseRepository responses, VenueCommentRepository comments,
                               LocationRepository locations, UserRepository users, ProjectAccess access, BlockingTransactions db,
                               ActivityLog activity) {
        this.activity = activity;
        this.links = links;
        this.responses = responses;
        this.comments = comments;
        this.locations = locations;
        this.users = users;
        this.access = access;
        this.db = db;
    }

    // --- for the crew -----------------------------------------------------------------------------------------

    /** The link to the whole project, or to the scene when {@code sceneId} is set; 404 while there is none. */
    public Mono<DirectorLinkResponse> link(UUID userId, UUID projectId, UUID sceneId) {
        return db.call(() -> {
            Scope scope = scope(userId, projectId, sceneId, ProjectRole.VIEWER);
            return find(scope).map(DirectorLinkResponse::from)
                    .orElseThrow(() -> new NotFoundException(scope.scene == null ? "Director link of project" : "Director link of scene",
                            scope.scene == null ? projectId : sceneId));
        });
    }

    /** A new link, replacing the one before (which stops working). Showing private details takes the owner. */
    public Mono<DirectorLinkResponse> share(UUID userId, UUID projectId, UUID sceneId, DirectorLinkRequest request) {
        boolean showPrivate = request != null && request.privateShown();
        return db.call(() -> {
            Scope scope = scope(userId, projectId, sceneId, showPrivate ? ProjectRole.OWNER : ProjectRole.EDITOR);
            find(scope).ifPresent(old -> {
                links.delete(old);
                links.flush();
            });
            DirectorLink link = new DirectorLink(scope.project, scope.scene, SecretTokens.newToken(), showPrivate,
                    users.getReferenceById(userId));
            return DirectorLinkResponse.from(links.saveAndFlush(link));
        });
    }

    /** Withdraws the link: it stops working at once. The calls already given stay. Doing it twice is harmless. */
    public Mono<Void> stopSharing(UUID userId, UUID projectId, UUID sceneId) {
        return db.run(() -> find(scope(userId, projectId, sceneId, ProjectRole.EDITOR)).ifPresent(links::delete));
    }

    /** The calls guests gave on a venue, latest first. */
    public Mono<PageResponse<DirectorResponseResponse>> forLocation(UUID userId, UUID locationId, PageQuery page) {
        return db.call(() -> {
            access.location(userId, locationId, ProjectRole.VIEWER);
            return PageResponse.from(responses.findByLocation(locationId, page.pageable()), DirectorResponseResponse::from);
        });
    }

    /** The calls guests gave on a scene's venues, latest first, for comparing them. */
    public Mono<PageResponse<DirectorResponseResponse>> forScene(UUID userId, UUID sceneId, PageQuery page) {
        return db.call(() -> {
            access.scene(userId, sceneId, ProjectRole.VIEWER);
            return PageResponse.from(responses.findByScene(sceneId, page.pageable()), DirectorResponseResponse::from);
        });
    }

    // --- for the guest, by token ------------------------------------------------------------------------------

    public Mono<PublicShortlistResponse> shortlist(String token, PageQuery page) {
        return db.call(() -> {
            DirectorLink link = byToken(token);
            Page<Location> venues = link.getScene() == null
                    ? locations.findProjectShortlist(link.getProject().getId(), SHOWN, page.pageable())
                    : locations.findSceneShortlist(link.getScene().getId(), SHOWN, page.pageable());
            Map<UUID, List<DirectorResponseResponse>> calls = venues.isEmpty() ? Map.of()
                    : responses.findByLocations(venues.map(Location::getId).getContent()).stream()
                            .map(DirectorResponseResponse::from)
                            .collect(Collectors.groupingBy(DirectorResponseResponse::locationId));
            return new PublicShortlistResponse(link.getProject().getTitle(),
                    link.getScene() == null ? null : link.getScene().getTitle(),
                    link.getCreatedBy() == null ? null : link.getCreatedBy().getDisplayName(),
                    link.isShowPrivate(),
                    PageResponse.from(venues, venue -> ShortlistVenueResponse.from(venue, link.isShowPrivate(),
                            calls.getOrDefault(venue.getId(), List.of()))));
        });
    }

    /**
     * Records a guest's call on one of the link's venues; the same name answering again replaces their earlier call.
     * A venue the link does not show (another project's, or one taken off the shortlist) is not found.
     */
    public Mono<DirectorResponseResponse> respond(String token, UUID locationId, DirectorResponseRequest request) {
        return db.call(() -> {
            DirectorLink link = byToken(token);
            Location venue = locations.findById(locationId)
                    .filter(location -> shows(link, location))
                    .orElseThrow(() -> new NotFoundException("Venue", locationId));
            String name = request.guestName().strip();
            String comment = blankToNull(request.comment());
            responses.upsert(venue.getId(), name, request.verdict().name(), comment);
            DirectorResponse saved = responses.findByLocations(List.of(venue.getId())).stream()
                    .filter(response -> response.getGuestName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElseThrow();
            followInThread(locations.getReferenceById(venue.getId()), saved, comment);
            activity.recordGuest(link.getProject(), saved.getGuestName(), ActivityVerb.DIRECTOR_CALLED, ActivityTarget.LOCATION, venue.getId(),
                    ActivityLog.facts("venue", venue.getName(), "verdict", saved.getVerdict().name(), "commented", comment != null));
            return DirectorResponseResponse.from(saved);
        });
    }

    /**
     * The guest's comment, in the venue's comment thread: made with their first comment, kept in step as they change
     * their call, and taken away when they clear it.
     */
    private void followInThread(Location venue, DirectorResponse response, String comment) {
        Optional<VenueComment> existing = comments.findByDirectorResponseId(response.getId());
        if (comment == null) {
            existing.ifPresent(comments::delete);
        } else if (existing.isPresent()) {
            existing.get().followGuest(response.getGuestName(), comment);
            comments.save(existing.get());
        } else {
            comments.save(VenueComment.byGuest(venue, response, response.getGuestName(), comment));
        }
    }

    private static boolean shows(DirectorLink link, Location location) {
        Scene scene = location.getScene();
        return SHOWN.contains(location.getStatus())
                && scene.getProject().getId().equals(link.getProject().getId())
                && (link.getScene() == null || link.getScene().getId().equals(scene.getId()));
    }

    private DirectorLink byToken(String token) {
        if (!SecretTokens.wellFormed(token)) {
            throw new NotFoundException("This shortlist is not shared, or no longer is");
        }
        return links.findByToken(token).orElseThrow(() -> new NotFoundException("This shortlist is not shared, or no longer is"));
    }

    // --- scope ------------------------------------------------------------------------------------------------

    /** The project, and the scene when the link is for one; the scene must belong to the project. */
    private record Scope(Project project, Scene scene) {
    }

    private Scope scope(UUID userId, UUID projectId, UUID sceneId, ProjectRole needed) {
        if (sceneId == null) {
            return new Scope(access.project(userId, projectId, needed), null);
        }
        Scene scene = access.scene(userId, sceneId, needed);
        if (projectId != null && !scene.getProject().getId().equals(projectId)) {
            throw new NotFoundException("Scene", sceneId);
        }
        return new Scope(scene.getProject(), scene);
    }

    private Optional<DirectorLink> find(Scope scope) {
        return scope.scene == null ? links.findForProject(scope.project.getId()) : links.findBySceneId(scope.scene.getId());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
