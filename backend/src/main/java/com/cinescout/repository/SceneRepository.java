package com.cinescout.repository;

import com.cinescout.domain.ParseStatus;
import com.cinescout.domain.Scene;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SceneRepository extends JpaRepository<Scene, UUID> {

    /**
     * The scene, with its project, only if the project is one {@code userId} is a member of. Membership is part
     * of the lookup, not a separate check, so a scene of someone else's project looks exactly like one that does not exist.
     */
    @Query("select s from Scene s join fetch s.project p where s.id = :sceneId and exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)")
    Optional<Scene> findVisible(@Param("sceneId") UUID sceneId, @Param("userId") UUID userId);

    /** A project's scenes in script order (numbered ones first, by number), if the project is one the user is a member of. */
    @Query(value = """
            select s from Scene s join fetch s.project p
            where p.id = :projectId and exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)
            order by s.sceneNumber asc nulls last, s.createdAt asc, s.id asc""",
            countQuery = "select count(s) from Scene s where s.project.id = :projectId and exists (select m.id from ProjectMember m where m.project = s.project and m.user.id = :userId)")
    Page<Scene> findVisibleByProject(@Param("projectId") UUID projectId, @Param("userId") UUID userId, Pageable pageable);

    /**
     * As {@link #findVisibleByProject}, only the scenes whose title, script or extracted setting contains
     * {@code pattern}: a LIKE pattern in lower case, with {@code \} as its escape character.
     */
    @Query(value = """
            select s from Scene s join fetch s.project p
            where p.id = :projectId and exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)
              and (lower(s.title) like :pattern escape '\\' or lower(s.sourceText) like :pattern escape '\\'
                   or lower(s.settingType) like :pattern escape '\\')
            order by s.sceneNumber asc nulls last, s.createdAt asc, s.id asc""",
            countQuery = """
            select count(s) from Scene s
            where s.project.id = :projectId and exists (select m.id from ProjectMember m where m.project = s.project and m.user.id = :userId)
              and (lower(s.title) like :pattern escape '\\' or lower(s.sourceText) like :pattern escape '\\'
                   or lower(s.settingType) like :pattern escape '\\')""")
    Page<Scene> searchVisibleByProject(@Param("projectId") UUID projectId, @Param("userId") UUID userId,
                                     @Param("pattern") String pattern, Pageable pageable);

    /** The scene numbers in use in a project; each can be used once (uq_scenes_project_number). */
    @Query("select s.sceneNumber from Scene s where s.project.id = :projectId and s.sceneNumber is not null")
    Set<Integer> findSceneNumbersByProjectId(@Param("projectId") UUID projectId);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndParseStatus(UUID projectId, ParseStatus parseStatus);

    /** The first scenes of a project in the given parse state, in script order. */
    @Query("""
            select s.id from Scene s where s.project.id = :projectId and s.parseStatus = :status
            order by s.sceneNumber asc nulls last, s.createdAt asc, s.id asc""")
    List<UUID> findIdsByProjectAndParseStatus(@Param("projectId") UUID projectId, @Param("status") ParseStatus status, Pageable pageable);

    /** How many scenes each of the given projects has; projects without scenes are left out. */
    @Query("select s.project.id as projectId, count(s) as total from Scene s where s.project.id in :projectIds group by s.project.id")
    List<ProjectCount> countByProjects(@Param("projectIds") Collection<UUID> projectIds);

    interface ProjectCount {
        UUID getProjectId();

        long getTotal();
    }

    /**
     * The dated scenes of the active projects the person is on, each with its project, earliest first, at most a page:
     * the calendar feed's events.
     */
    @Query("""
            select s from Scene s join fetch s.project p
            where p.status = com.cinescout.domain.ProjectStatus.ACTIVE
              and exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)
              and (s.shootDateStart is not null or s.shootDateEnd is not null)
            order by coalesce(s.shootDateStart, s.shootDateEnd) asc, s.id asc""")
    List<Scene> findDatedForMember(@Param("userId") UUID userId, Pageable pageable);
}
