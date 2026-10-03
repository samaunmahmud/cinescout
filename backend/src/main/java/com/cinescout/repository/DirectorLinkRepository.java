package com.cinescout.repository;

import com.cinescout.domain.DirectorLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DirectorLinkRepository extends JpaRepository<DirectorLink, UUID> {

    @Query("select d from DirectorLink d join fetch d.project left join fetch d.scene where d.token = :token")
    Optional<DirectorLink> findByToken(@Param("token") String token);

    /** The link to the whole project, not to one of its scenes. */
    @Query("select d from DirectorLink d where d.project.id = :projectId and d.scene is null")
    Optional<DirectorLink> findForProject(@Param("projectId") UUID projectId);

    Optional<DirectorLink> findBySceneId(UUID sceneId);
}
