package com.cinescout.repository;

import com.cinescout.domain.SceneCover;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SceneCoverRepository extends JpaRepository<SceneCover, UUID> {

    /** A scene's covers in the order they were added. Access is checked on the scene before this is asked. */
    @Query(value = "select c from SceneCover c join fetch c.location left join fetch c.addedBy where c.scene.id = :sceneId order by c.createdAt asc, c.id asc",
            countQuery = "select count(c) from SceneCover c where c.scene.id = :sceneId")
    Page<SceneCover> findByScene(@Param("sceneId") UUID sceneId, Pageable pageable);

    @Query("select count(c) from SceneCover c where c.scene.id = :sceneId")
    long countByScene(@Param("sceneId") UUID sceneId);

    @Query("select count(c) > 0 from SceneCover c where c.scene.id = :sceneId and c.location.id = :locationId")
    boolean exists(@Param("sceneId") UUID sceneId, @Param("locationId") UUID locationId);

    /** A cover with its scene and project, for checking the user's role on it. */
    @Query("select c from SceneCover c join fetch c.scene s join fetch s.project join fetch c.location where c.id = :id")
    Optional<SceneCover> findWithScene(@Param("id") UUID id);

    /** Every cover of a project's scenes, each with its venue, in the order they were added, for the schedule. */
    @Query("select c from SceneCover c join fetch c.location where c.scene.project.id = :projectId order by c.createdAt asc, c.id asc")
    List<SceneCover> findByProject(@Param("projectId") UUID projectId);

    /** The covers of these scenes, each with its venue, in the order they were added. */
    @Query("select c from SceneCover c join fetch c.location where c.scene.id in :sceneIds order by c.createdAt asc, c.id asc")
    List<SceneCover> findByScenes(@Param("sceneIds") Collection<UUID> sceneIds);
}
