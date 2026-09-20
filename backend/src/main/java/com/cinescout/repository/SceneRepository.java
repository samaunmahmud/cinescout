package com.cinescout.repository;

import com.cinescout.domain.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SceneRepository extends JpaRepository<Scene, UUID> {

    /**
     * The scene, with its project, only if the project belongs to {@code ownerId}. Ownership is a
     * join, not a separate check, so a scene of someone else looks exactly like one that does not exist.
     */
    @Query("select s from Scene s join fetch s.project p where s.id = :sceneId and p.owner.id = :ownerId")
    Optional<Scene> findOwned(@Param("sceneId") UUID sceneId, @Param("ownerId") UUID ownerId);
}
