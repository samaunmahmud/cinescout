package com.cinescout.repository;

import com.cinescout.domain.ProjectMember;
import com.cinescout.domain.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    @Query("select m.role from ProjectMember m where m.project.id = :projectId and m.user.id = :userId")
    Optional<ProjectRole> findRole(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    @Query("select m from ProjectMember m join fetch m.user where m.project.id = :projectId and m.user.id = :userId")
    Optional<ProjectMember> findMember(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    /** A project's crew, owner first, then editors, then viewers, each by when they joined. */
    @Query("""
            select m from ProjectMember m join fetch m.user
            where m.project.id = :projectId
            order by case m.role when com.cinescout.domain.ProjectRole.OWNER then 0
                                 when com.cinescout.domain.ProjectRole.EDITOR then 1 else 2 end,
                     m.joinedAt asc, m.id asc""")
    List<ProjectMember> findCrew(@Param("projectId") UUID projectId);

    /** The user's role in each of the given projects. */
    /** The people on these projects, each project's owner first, then in the order they joined. */
    @Query("""
            select m.project.id as projectId, u.displayName as name from ProjectMember m join m.user u
            where m.project.id in :projectIds
            order by m.project.id, case when m.role = com.cinescout.domain.ProjectRole.OWNER then 0 else 1 end, m.joinedAt asc, m.id asc""")
    List<CrewNameRow> findCrewNames(@Param("projectIds") Collection<UUID> projectIds);

    interface CrewNameRow {
        UUID getProjectId();
        String getName();
    }

    @Query("select m.project.id as projectId, m.role as role from ProjectMember m where m.user.id = :userId and m.project.id in :projectIds")
    List<ProjectRoleRow> findRoles(@Param("userId") UUID userId, @Param("projectIds") Collection<UUID> projectIds);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    interface ProjectRoleRow {
        UUID getProjectId();

        ProjectRole getRole();
    }
}
