package com.cinescout.repository;

import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    /** The location with its scene, only if the scene's project is one {@code userId} is a member of. */
    @Query("""
            select l from Location l join fetch l.scene s join fetch s.project p
            where l.id = :locationId and exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)""")
    Optional<Location> findVisible(@Param("locationId") UUID locationId, @Param("userId") UUID userId);

    /** A scene's locations, best fit first; ones without a score (added by hand) come last. */
    @Query(value = """
            select l from Location l join fetch l.scene s
            where s.id = :sceneId and exists (select m.id from ProjectMember m where m.project = s.project and m.user.id = :userId)
            order by l.fitScore desc nulls last, l.createdAt asc, l.id asc""",
            countQuery = "select count(l) from Location l where l.scene.id = :sceneId and exists (select m.id from ProjectMember m where m.project = l.scene.project and m.user.id = :userId)")
    Page<Location> findVisibleByScene(@Param("sceneId") UUID sceneId, @Param("userId") UUID userId, Pageable pageable);

    /**
     * The venues a director link to a whole project shows: those in {@code statuses} (the shortlist and beyond),
     * scene by scene in script order and best fit first. No user check: the link's token is the permission, so
     * callers check that first.
     */
    @Query(value = """
            select l from Location l join fetch l.scene s
            where s.project.id = :projectId and l.status in :statuses
            order by s.sceneNumber asc nulls last, s.createdAt asc, s.id asc, l.fitScore desc nulls last, l.createdAt asc, l.id asc""",
            countQuery = "select count(l) from Location l where l.scene.project.id = :projectId and l.status in :statuses")
    Page<Location> findProjectShortlist(@Param("projectId") UUID projectId, @Param("statuses") Collection<LocationStatus> statuses,
                                        Pageable pageable);

    /** As {@link #findProjectShortlist}, for a link to one scene. */
    @Query(value = """
            select l from Location l join fetch l.scene s
            where s.id = :sceneId and l.status in :statuses
            order by l.fitScore desc nulls last, l.createdAt asc, l.id asc""",
            countQuery = "select count(l) from Location l where l.scene.id = :sceneId and l.status in :statuses")
    Page<Location> findSceneShortlist(@Param("sceneId") UUID sceneId, @Param("statuses") Collection<LocationStatus> statuses,
                                      Pageable pageable);

    /** The pages already saved for a scene; the same page must not be saved twice (uq_locations_scene_source). */
    @Query("select l.sourceUrl from Location l where l.scene.id = :sceneId and l.sourceUrl is not null")
    Set<String> findSourceUrlsBySceneId(@Param("sceneId") UUID sceneId);

    /** A saved venue as scouting compares new finds with it: by page, name, street address or listing. */
    interface SavedVenueRow {
        String getName();

        String getAddress();

        String getSourceUrl();
    }

    @Query("select l.name as name, l.address as address, l.sourceUrl as sourceUrl from Location l where l.scene.id = :sceneId")
    List<SavedVenueRow> findSavedVenues(@Param("sceneId") UUID sceneId);

    /** Why the crew passed on the scene's venues, latest first; only the rejected ones that say. */
    @Query("""
            select l.rejectionReason from Location l
            where l.scene.id = :sceneId and l.status = com.cinescout.domain.LocationStatus.REJECTED and l.rejectionReason is not null
            order by l.updatedAt desc, l.id desc""")
    List<String> findRejectionReasons(@Param("sceneId") UUID sceneId, Pageable pageable);

    /** The names of a scene's locations, so a venue found again on another page is recognised as already saved. */
    @Query("select l.name from Location l where l.scene.id = :sceneId")
    Set<String> findNamesBySceneId(@Param("sceneId") UUID sceneId);

    /**
     * Every candidate location of a project, scene by scene in script order and best fit first within a scene.
     * The scene is fetched with it, as each row is shown with the scene it is for.
     */
    @Query(value = """
            select l from Location l join fetch l.scene s
            where s.project.id = :projectId and exists (select m.id from ProjectMember m where m.project = s.project and m.user.id = :userId)
            order by s.sceneNumber asc nulls last, s.createdAt asc, s.id asc, l.fitScore desc nulls last, l.createdAt asc, l.id asc""",
            countQuery = "select count(l) from Location l where l.scene.project.id = :projectId and exists (select m.id from ProjectMember m where m.project = l.scene.project and m.user.id = :userId)")
    Page<Location> findVisibleByProject(@Param("projectId") UUID projectId, @Param("userId") UUID userId, Pageable pageable);

    /** As {@link #findVisibleByProject}, only the locations in one status. */
    @Query(value = """
            select l from Location l join fetch l.scene s
            where s.project.id = :projectId and exists (select m.id from ProjectMember m where m.project = s.project and m.user.id = :userId) and l.status = :status
            order by s.sceneNumber asc nulls last, s.createdAt asc, s.id asc, l.fitScore desc nulls last, l.createdAt asc, l.id asc""",
            countQuery = """
                    select count(l) from Location l
                    where l.scene.project.id = :projectId and exists (select m.id from ProjectMember m where m.project = l.scene.project and m.user.id = :userId) and l.status = :status""")
    Page<Location> findVisibleByProjectAndStatus(@Param("projectId") UUID projectId, @Param("userId") UUID userId,
                                               @Param("status") LocationStatus status, Pageable pageable);

    /** How many locations a project has in each status, and over how many scenes they are spread. */
    @Query("""
            select l.status as status, count(l) as locations, count(distinct l.scene.id) as scenes
            from Location l where l.scene.project.id = :projectId group by l.status""")
    List<StatusCount> countByStatus(@Param("projectId") UUID projectId);

    /** The scenes of a project that have at least one candidate location. */
    @Query("select count(distinct l.scene.id) from Location l where l.scene.project.id = :projectId")
    long countScenesWithLocations(@Param("projectId") UUID projectId);

    /** How many candidate locations each scene of a project has; scenes without any are left out. */
    @Query("select l.scene.id as sceneId, count(l) as locations from Location l where l.scene.project.id = :projectId group by l.scene.id")
    List<SceneCount> countByScene(@Param("projectId") UUID projectId);

    interface SceneCount {
        UUID getSceneId();

        long getLocations();
    }

    /** How many scenes of each of the given projects have a location in {@code status}; projects with none are left out. */
    @Query("""
            select l.scene.project.id as projectId, count(distinct l.scene.id) as total from Location l
            where l.scene.project.id in :projectIds and l.status = :status group by l.scene.project.id""")
    List<SceneRepository.ProjectCount> countScenesByProjectsAndStatus(@Param("projectIds") Collection<UUID> projectIds,
                                                                      @Param("status") LocationStatus status);

    /**
     * The venues with a picture in the given projects, the ones most worth showing first: confirmed, then
     * shortlisted or contacted, then the rest, best fit first. The caller keeps the first per project.
     */
    @Query("""
            select l.scene.project.id as projectId, l.imageUrl as imageUrl from Location l
            where l.scene.project.id in :projectIds and l.imageUrl is not null and l.status <> com.cinescout.domain.LocationStatus.REJECTED
            order by case l.status when com.cinescout.domain.LocationStatus.CONFIRMED then 0
                                   when com.cinescout.domain.LocationStatus.SHORTLISTED then 1
                                   when com.cinescout.domain.LocationStatus.CONTACTED then 1 else 2 end,
                     l.fitScore desc nulls last, l.createdAt asc""")
    List<ProjectImage> findImagesByProjects(@Param("projectIds") Collection<UUID> projectIds);

    interface ProjectImage {
        UUID getProjectId();

        String getImageUrl();
    }

    interface StatusCount {
        LocationStatus getStatus();

        long getLocations();

        long getScenes();
    }

    /**
     * The confirmed venues with a position whose scene shoots on any day from {@code first} to {@code last}, in active
     * projects, each with its scene and project, the earliest shoot first: what the weather watch looks at.
     */
    @Query("""
            select l from Location l join fetch l.scene s join fetch s.project p
            where p.status = com.cinescout.domain.ProjectStatus.ACTIVE and l.status = com.cinescout.domain.LocationStatus.CONFIRMED
              and l.latitude is not null and l.longitude is not null
              and coalesce(s.shootDateStart, s.shootDateEnd) <= :last and coalesce(s.shootDateEnd, s.shootDateStart) >= :first
            order by coalesce(s.shootDateStart, s.shootDateEnd) asc, l.id asc""")
    List<Location> findShootingBetween(@Param("first") LocalDate first, @Param("last") LocalDate last, Pageable pageable);
}
