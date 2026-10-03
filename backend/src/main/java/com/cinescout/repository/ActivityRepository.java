package com.cinescout.repository;

import com.cinescout.domain.Activity;
import com.cinescout.domain.ActivityKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    /** A project's log, newest first. */
    @Query(value = "select a from Activity a where a.project.id = :projectId order by a.createdAt desc, a.id desc",
            countQuery = "select count(a) from Activity a where a.project.id = :projectId")
    Page<Activity> findByProject(@Param("projectId") UUID projectId, Pageable pageable);

    /** As {@link #findByProject}, one kind only. */
    @Query(value = "select a from Activity a where a.project.id = :projectId and a.kind = :kind order by a.createdAt desc, a.id desc",
            countQuery = "select count(a) from Activity a where a.project.id = :projectId and a.kind = :kind")
    Page<Activity> findByProjectAndKind(@Param("projectId") UUID projectId, @Param("kind") ActivityKind kind, Pageable pageable);
}
