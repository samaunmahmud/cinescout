package com.cinescout.repository;

import com.cinescout.domain.OutreachDraft;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OutreachDraftRepository extends JpaRepository<OutreachDraft, UUID> {

    /** The draft with its location, only if the location's project belongs to {@code ownerId}. */
    @Query("""
            select d from OutreachDraft d join fetch d.location l join l.scene s join s.project p
            where d.id = :draftId and p.owner.id = :ownerId""")
    Optional<OutreachDraft> findOwned(@Param("draftId") UUID draftId, @Param("ownerId") UUID ownerId);

    /** A location's drafts, newest first, if the location is the owner's. */
    @Query(value = """
            select d from OutreachDraft d join fetch d.location l join l.scene s join s.project p
            where l.id = :locationId and p.owner.id = :ownerId
            order by d.createdAt desc, d.id desc""",
            countQuery = """
            select count(d) from OutreachDraft d join d.location l join l.scene s join s.project p
            where l.id = :locationId and p.owner.id = :ownerId""")
    Page<OutreachDraft> findOwnedByLocation(@Param("locationId") UUID locationId, @Param("ownerId") UUID ownerId, Pageable pageable);
}
