package com.cinescout.repository;

import com.cinescout.domain.ProjectInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectInviteRepository extends JpaRepository<ProjectInvite, UUID> {

    @Query("select i from ProjectInvite i join fetch i.project p join fetch p.owner where i.tokenHash = :tokenHash")
    Optional<ProjectInvite> findByTokenHash(@Param("tokenHash") String tokenHash);

    /** A project's invites that can still be accepted, newest first. */
    @Query("""
            select i from ProjectInvite i
            where i.project.id = :projectId and i.acceptedAt is null and i.expiresAt > :now
            order by i.createdAt desc, i.id desc""")
    List<ProjectInvite> findOpen(@Param("projectId") UUID projectId, @Param("now") Instant now);

    Optional<ProjectInvite> findByIdAndProjectId(UUID id, UUID projectId);
}
