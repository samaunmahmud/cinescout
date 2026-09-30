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
     * The scene, with its project, only if the project belongs to {@code ownerId}. Ownership is a
     * join, not a separate check, so a scene of someone else looks exactly like one that does not exist.
     */
    @Query("select s from Scene s join fetch s.project p where s.id = :sceneId and p.owner.id = :ownerId")
    Optional<Scene> findOwned(@Param("sceneId") UUID sceneId, @Param("ownerId") UUID ownerId);

    /** A project's scenes in script order (numbered ones first, by number), if the project is the owner's. */
    @Query(value = """
            select s from Scene s join fetch s.project p
            where p.id = :projectId and p.owner.id = :ownerId
            order by s.sceneNumber asc nulls last, s.createdAt asc, s.id asc""",
            countQuery = "select count(s) from Scene s where s.project.id = :projectId and s.project.owner.id = :ownerId")
    Page<Scene> findOwnedByProject(@Param("projectId") UUID projectId, @Param("ownerId") UUID ownerId, Pageable pageable);

    /**
     * As {@link #findOwnedByProject}, only the scenes whose title, script or extracted setting contains
     * {@code pattern}: a LIKE pattern in lower case, with {@code \} as its escape character.
     */
    @Query(value = """
            select s from Scene s join fetch s.project p
            where p.id = :projectId and p.owner.id = :ownerId
              and (lower(s.title) like :pattern escape '\\' or lower(s.sourceText) like :pattern escape '\\'
                   or lower(s.settingType) like :pattern escape '\\')
            order by s.sceneNumber asc nulls last, s.createdAt asc, s.id asc""",
            countQuery = """
            select count(s) from Scene s
            where s.project.id = :projectId and s.project.owner.id = :ownerId
              and (lower(s.title) like :pattern escape '\\' or lower(s.sourceText) like :pattern escape '\\'
                   or lower(s.settingType) like :pattern escape '\\')""")
    Page<Scene> searchOwnedByProject(@Param("projectId") UUID projectId, @Param("ownerId") UUID ownerId,
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
}
