package com.cinescout.repository;

import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /** Ownership is part of the lookup, so someone else's project looks exactly like a missing one. */
    Optional<Project> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Project> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<Project> findByOwnerIdAndStatusOrderByCreatedAtDesc(UUID ownerId, ProjectStatus status);
}
