package com.cinescout.repository;

import com.cinescout.domain.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** A person only ever sees their own alerts, and only from projects they are still on. */
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    String ON_PROJECT = "exists (select m.id from ProjectMember m where m.project = a.project and m.user.id = :userId)";

    /** Newest first; {@code unreadOnly} keeps the ones not read yet. */
    @Query(value = "select a from Alert a join fetch a.project where a.userId = :userId and (:unreadOnly = false or a.readAt is null) and "
            + ON_PROJECT + " order by a.createdAt desc, a.id desc",
            countQuery = "select count(a) from Alert a where a.userId = :userId and (:unreadOnly = false or a.readAt is null) and " + ON_PROJECT)
    Page<Alert> findMine(@Param("userId") UUID userId, @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

    @Query("select count(a) from Alert a where a.userId = :userId and a.readAt is null and " + ON_PROJECT)
    long countUnread(@Param("userId") UUID userId);

    @Query("select a from Alert a where a.id = :id and a.userId = :userId and " + ON_PROJECT)
    Optional<Alert> findMine(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Alert a set a.readAt = :now where a.id = :id and a.userId = :userId and a.readAt is null")
    int markRead(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Alert a set a.readAt = :now where a.userId = :userId and a.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Makes an alert for each member of the project, once per member and key; returns how many were new. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into alerts (user_id, project_id, kind, scene_id, location_id, draft_id, payload, dedupe_key)
            select m.user_id, m.project_id, :kind, :sceneId, :locationId, null, cast(:payload as jsonb), :dedupeKey
            from project_members m where m.project_id = :projectId
            on conflict (user_id, dedupe_key) do nothing""")
    int raiseForCrew(@Param("projectId") UUID projectId, @Param("kind") String kind, @Param("sceneId") UUID sceneId,
                     @Param("locationId") UUID locationId, @Param("payload") String payload, @Param("dedupeKey") String dedupeKey);

    /**
     * Makes a follow-up alert, for the owner and editors of its project, for every email flagged for a follow-up that is
     * still waiting (sent, unanswered, not followed up). Each once per person; returns how many were new.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into alerts (user_id, project_id, kind, scene_id, location_id, draft_id, payload, dedupe_key)
            select m.user_id, p.id, 'FOLLOW_UP', s.id, l.id, d.id,
                   jsonb_build_object('venue', l.name, 'scene', s.title, 'subject', d.subject, 'sentAt', d.sent_at),
                   'follow-up:' || d.id
            from outreach_drafts d
            join locations l on l.id = d.location_id
            join scenes s on s.id = l.scene_id
            join projects p on p.id = s.project_id
            join project_members m on m.project_id = p.id and m.role in ('OWNER', 'EDITOR')
            where d.follow_up_flagged_at is not null and d.status = 'SENT' and p.status = 'ACTIVE'
              and not exists (select 1 from outreach_drafts f where f.follow_up_of = d.id)
              and not exists (select 1 from outreach_replies r where r.draft_id = d.id)
            on conflict (user_id, dedupe_key) do nothing""")
    int raiseFollowUps();

    /** Forgets alerts older than {@code before}; returns how many. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Alert a where a.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
