package com.cinescout.repository;

import com.cinescout.domain.VenueComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenueCommentRepository extends JpaRepository<VenueComment, UUID> {

    /** A venue's threads: its top-level comments, oldest first, with their authors. */
    @Query(value = "select c from VenueComment c left join fetch c.author where c.location.id = :locationId and c.parent is null "
            + "order by c.createdAt asc, c.id asc",
            countQuery = "select count(c) from VenueComment c where c.location.id = :locationId and c.parent is null")
    Page<VenueComment> findThreads(@Param("locationId") UUID locationId, Pageable pageable);

    /** The replies to these comments, oldest first, with their authors. */
    @Query("select c from VenueComment c left join fetch c.author where c.parent.id in :parentIds order by c.createdAt asc, c.id asc")
    List<VenueComment> findReplies(@Param("parentIds") Collection<UUID> parentIds);

    /** The comment with its venue, scene and project, for the access check. */
    @Query("select c from VenueComment c join fetch c.location l join fetch l.scene s join fetch s.project left join fetch c.author where c.id = :id")
    Optional<VenueComment> findWithProject(@Param("id") UUID id);

    Optional<VenueComment> findByDirectorResponseId(UUID directorResponseId);
}
