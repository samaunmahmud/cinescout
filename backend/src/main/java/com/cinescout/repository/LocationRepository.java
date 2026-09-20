package com.cinescout.repository;

import com.cinescout.domain.Location;
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
    @Query("""
            select l from Location l join fetch l.scene s
            where s.id = :sceneId and s.project.owner.id = :ownerId
            order by l.fitScore desc nulls last, l.createdAt asc""")
    List<Location> findOwnedByScene(@Param("sceneId") UUID sceneId, @Param("ownerId") UUID ownerId);

    /** The pages already saved for a scene; the same page must not be saved twice (uq_locations_scene_source). */
    @Query("select l.sourceUrl from Location l where l.scene.id = :sceneId and l.sourceUrl is not null")
    Set<String> findSourceUrlsBySceneId(@Param("sceneId") UUID sceneId);
}
