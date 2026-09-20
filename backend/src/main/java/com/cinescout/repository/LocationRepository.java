package com.cinescout.repository;

import com.cinescout.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    /** The pages already saved for a scene; the same page must not be saved twice (uq_locations_scene_source). */
    @Query("select l.sourceUrl from Location l where l.scene.id = :sceneId and l.sourceUrl is not null")
    Set<String> findSourceUrlsBySceneId(@Param("sceneId") UUID sceneId);
}
