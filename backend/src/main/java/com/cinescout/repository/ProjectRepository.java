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

    /** Ownership is part of the lookup, so someone else's project looks exactly like a missing one. */
    Optional<Project> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<Project> findByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId, Pageable pageable);

    Page<Project> findByOwnerIdAndStatusOrderByCreatedAtDescIdDesc(UUID ownerId, ProjectStatus status, Pageable pageable);

    /** The project whose call sheet is shared under {@code token}, with its owner (whose name the sheet carries). */
    @Query("select p from Project p join fetch p.owner where p.callSheetToken = :token")
    Optional<Project> findByCallSheetToken(@Param("token") String token);
}
