package com.cinescout.repository;

import com.cinescout.domain.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SceneRepository extends JpaRepository<Scene, UUID> {

    /**
     * The scene, with its project, only if the project belongs to {@code ownerId}. Ownership is a
     * join, not a separate check, so a scene of someone else looks exactly like one that does not exist.
     */
    @Query("select s from Scene s join fetch s.project p where s.id = :sceneId and p.owner.id = :ownerId")
    Optional<Scene> findOwned(@Param("sceneId") UUID sceneId, @Param("ownerId") UUID ownerId);

    /** A project's scenes in script order (numbered ones first, by number), if the project is the owner's. */
    @Query("""
            select s from Scene s join fetch s.project p
            where p.id = :projectId and p.owner.id = :ownerId
            order by s.sceneNumber asc nulls last, s.createdAt asc""")
    List<Scene> findOwnedByProject(@Param("projectId") UUID projectId, @Param("ownerId") UUID ownerId);
}
