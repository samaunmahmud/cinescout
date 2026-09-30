package com.cinescout.repository;

import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    /** The location with its scene, only if the scene's project belongs to {@code ownerId}. */
    @Query("""
            select l from Location l join fetch l.scene s join fetch s.project p
            where l.id = :locationId and p.owner.id = :ownerId""")
    Optional<Location> findOwned(@Param("locationId") UUID locationId, @Param("ownerId") UUID ownerId);

    /** A scene's locations, best fit first; ones without a score (added by hand) come last. */
    @Query(value = """
            select l from Location l join fetch l.scene s
            where s.id = :sceneId and s.project.owner.id = :ownerId
            order by l.fitScore desc nulls last, l.createdAt asc, l.id asc""",
            countQuery = "select count(l) from Location l where l.scene.id = :sceneId and l.scene.project.owner.id = :ownerId")
    Page<Location> findOwnedByScene(@Param("sceneId") UUID sceneId, @Param("ownerId") UUID ownerId, Pageable pageable);

    /** The pages already saved for a scene; the same page must not be saved twice (uq_locations_scene_source). */
    @Query("select l.sourceUrl from Location l where l.scene.id = :sceneId and l.sourceUrl is not null")
    Set<String> findSourceUrlsBySceneId(@Param("sceneId") UUID sceneId);

    /** The names of a scene's locations, so a venue found again on another page is recognised as already saved. */
    @Query("select l.name from Location l where l.scene.id = :sceneId")
    Set<String> findNamesBySceneId(@Param("sceneId") UUID sceneId);

    /**
     * Every candidate location of a project, scene by scene in script order and best fit first within a scene.
     * The scene is fetched with it, as each row is shown with the scene it is for.
     */
    @Query(value = """
            select l from Location l join fetch l.scene s
            where s.project.id = :projectId and s.project.owner.id = :ownerId
            order by s.sceneNumber asc nulls last, s.createdAt asc, s.id asc, l.fitScore desc nulls last, l.createdAt asc, l.id asc""",
            countQuery = "select count(l) from Location l where l.scene.project.id = :projectId and l.scene.project.owner.id = :ownerId")
    Page<Location> findOwnedByProject(@Param("projectId") UUID projectId, @Param("ownerId") UUID ownerId, Pageable pageable);

    /** As {@link #findOwnedByProject}, only the locations in one status. */
    @Query(value = """
            select l from Location l join fetch l.scene s
            where s.project.id = :projectId and s.project.owner.id = :ownerId and l.status = :status
            order by s.sceneNumber asc nulls last, s.createdAt asc, s.id asc, l.fitScore desc nulls last, l.createdAt asc, l.id asc""",
            countQuery = """
                    select count(l) from Location l
                    where l.scene.project.id = :projectId and l.scene.project.owner.id = :ownerId and l.status = :status""")
    Page<Location> findOwnedByProjectAndStatus(@Param("projectId") UUID projectId, @Param("ownerId") UUID ownerId,
                                               @Param("status") LocationStatus status, Pageable pageable);

    /** How many locations a project has in each status, and over how many scenes they are spread. */
    @Query("""
            select l.status as status, count(l) as locations, count(distinct l.scene.id) as scenes
            from Location l where l.scene.project.id = :projectId group by l.status""")
    List<StatusCount> countByStatus(@Param("projectId") UUID projectId);

    /** The scenes of a project that have at least one candidate location. */
    @Query("select count(distinct l.scene.id) from Location l where l.scene.project.id = :projectId")
    long countScenesWithLocations(@Param("projectId") UUID projectId);

    interface StatusCount {
        LocationStatus getStatus();

        long getLocations();

        long getScenes();
    }
}
