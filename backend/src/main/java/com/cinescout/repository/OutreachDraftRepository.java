package com.cinescout.repository;

import com.cinescout.domain.OutreachDraft;
import com.cinescout.domain.OutreachStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutreachDraftRepository extends JpaRepository<OutreachDraft, UUID> {

    /** The draft with its location, only if the location's project is one {@code userId} is a member of. */
    @Query("""
            select d from OutreachDraft d join fetch d.location l join l.scene s join s.project p
            where d.id = :draftId and exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)""")
    Optional<OutreachDraft> findVisible(@Param("draftId") UUID draftId, @Param("userId") UUID userId);

    /** The draft a reply address belongs to. No user check: the token is what an inbound email carries. */
    Optional<OutreachDraft> findByReplyToken(String replyToken);

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

    /** As {@link #findVisibleByProject}, only the emails flagged for a follow-up, oldest sent first. */
    @Query(value = """
            select d from OutreachDraft d join fetch d.location l join fetch l.scene s
            where s.project.id = :projectId and exists (select m.id from ProjectMember m where m.project = s.project and m.user.id = :userId)
            and d.followUpFlaggedAt is not null
            order by d.sentAt asc, d.id asc""",
            countQuery = """
            select count(d) from OutreachDraft d
            where d.location.scene.project.id = :projectId and exists (select m.id from ProjectMember m where m.project = d.location.scene.project and m.user.id = :userId)
            and d.followUpFlaggedAt is not null""")
    Page<OutreachDraft> findVisibleByProjectDueForFollowUp(@Param("projectId") UUID projectId, @Param("userId") UUID userId, Pageable pageable);

    /**
     * Flags every email of an active project that was sent more than the project's follow-up days ago and has had no
     * reply and no follow-up since. Already flagged ones are left as they are.
     *
     * @return how many were flagged now
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            update outreach_drafts d set follow_up_flagged_at = now()
            from locations l join scenes s on s.id = l.scene_id join projects p on p.id = s.project_id
            where l.id = d.location_id and p.status = 'ACTIVE'
              and d.status = 'SENT' and d.follow_up_flagged_at is null
              and d.sent_at < now() - make_interval(days => p.follow_up_days)
              and not exists (select 1 from outreach_drafts f where f.follow_up_of = d.id)
              and not exists (select 1 from outreach_replies r where r.draft_id = d.id)""")
    int flagOverdue();

    /** How many emails wait on a follow-up, per project. */
    @Query("""
            select d.location.scene.project.id as projectId, count(d) as total from OutreachDraft d
            where d.location.scene.project.id in :projectIds and d.followUpFlaggedAt is not null
            group by d.location.scene.project.id""")
    List<SceneRepository.ProjectCount> countDueForFollowUpByProjects(@Param("projectIds") Collection<UUID> projectIds);
}
