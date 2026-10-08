package com.cinescout.repository;

import com.cinescout.domain.Shot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShotRepository extends JpaRepository<Shot, UUID> {

    /** A scene's shots in shooting order, each with its own venue if it names one. Access is checked on the scene first. */
    @Query("select s from Shot s left join fetch s.location where s.scene.id = :sceneId order by s.position asc, s.createdAt asc, s.id asc")
    List<Shot> findByScene(@Param("sceneId") UUID sceneId);

    @Query("select coalesce(max(s.position), -1) from Shot s where s.scene.id = :sceneId")
    int lastPosition(@Param("sceneId") UUID sceneId);

    @Query("select count(s) from Shot s where s.scene.id = :sceneId")
    long countByScene(@Param("sceneId") UUID sceneId);

    /** A shot with its scene and project, for checking the user's role on it. */
    @Query("select s from Shot s join fetch s.scene sc join fetch sc.project left join fetch s.location where s.id = :id")
    Optional<Shot> findWithScene(@Param("id") UUID id);
}
