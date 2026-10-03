package com.cinescout.repository;

import com.cinescout.domain.OutreachDraft;
import com.cinescout.domain.OutreachStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OutreachDraftRepository extends JpaRepository<OutreachDraft, UUID> {

    /** The draft with its location, only if the location's project is one {@code userId} is a member of. */
    @Query("""
            select d from OutreachDraft d join fetch d.location l join l.scene s join s.project p
            where d.id = :draftId and exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)""")
    Optional<OutreachDraft> findVisible(@Param("draftId") UUID draftId, @Param("userId") UUID userId);

    /** A location's drafts, newest first, if the location is one the user is a member of. */
    @Query(value = """
            select d from OutreachDraft d join fetch d.location l join l.scene s join s.project p
            where l.id = :locationId and exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)
            order by d.createdAt desc, d.id desc""",
            countQuery = """
            select count(d) from OutreachDraft d join d.location l join l.scene s join s.project p
            where l.id = :locationId and exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)""")
    Page<OutreachDraft> findVisibleByLocation(@Param("locationId") UUID locationId, @Param("userId") UUID userId, Pageable pageable);

    /** Every draft of a project, newest first, each with its location and scene. */
    @Query(value = """
            select d from OutreachDraft d join fetch d.location l join fetch l.scene s
            where s.project.id = :projectId and exists (select m.id from ProjectMember m where m.project = s.project and m.user.id = :userId)
            order by d.createdAt desc, d.id desc""",
            countQuery = """
            select count(d) from OutreachDraft d
            where d.location.scene.project.id = :projectId and exists (select m.id from ProjectMember m where m.project = d.location.scene.project and m.user.id = :userId)""")
    Page<OutreachDraft> findVisibleByProject(@Param("projectId") UUID projectId, @Param("userId") UUID userId, Pageable pageable);

    /** As {@link #findVisibleByProject}, only the drafts in one status. */
    @Query(value = """
            select d from OutreachDraft d join fetch d.location l join fetch l.scene s
            where s.project.id = :projectId and exists (select m.id from ProjectMember m where m.project = s.project and m.user.id = :userId) and d.status = :status
            order by d.createdAt desc, d.id desc""",
            countQuery = """
            select count(d) from OutreachDraft d
            where d.location.scene.project.id = :projectId and exists (select m.id from ProjectMember m where m.project = d.location.scene.project and m.user.id = :userId) and d.status = :status""")
    Page<OutreachDraft> findVisibleByProjectAndStatus(@Param("projectId") UUID projectId, @Param("userId") UUID userId,
                                                    @Param("status") OutreachStatus status, Pageable pageable);
}
