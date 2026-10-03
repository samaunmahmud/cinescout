package com.cinescout.repository;

import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /** Membership is part of the lookup, so a project the user is not on looks exactly like a missing one. */
    @Query("""
            select p from Project p
            where p.id = :projectId and exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)""")
    Optional<Project> findVisible(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    /** The projects the user is a member of, newest first. */
    @Query("""
            select p from Project p
            where exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)
            order by p.createdAt desc, p.id desc""")
    Page<Project> findVisible(@Param("userId") UUID userId, Pageable pageable);

    /** As {@link #findVisible(UUID, Pageable)}, in one status. */
    @Query("""
            select p from Project p
            where p.status = :status and exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)
            order by p.createdAt desc, p.id desc""")
    Page<Project> findVisibleByStatus(@Param("userId") UUID userId, @Param("status") ProjectStatus status, Pageable pageable);

    /** The project whose call sheet is shared under {@code token}, with its owner (whose name the sheet carries). */
    @Query("select p from Project p join fetch p.owner where p.callSheetToken = :token")
    Optional<Project> findByCallSheetToken(@Param("token") String token);
}
