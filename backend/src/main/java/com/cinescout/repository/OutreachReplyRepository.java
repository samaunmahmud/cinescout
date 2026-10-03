package com.cinescout.repository;

import com.cinescout.domain.OutreachReply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface OutreachReplyRepository extends JpaRepository<OutreachReply, UUID> {

    /** A draft's replies, latest first, with who recorded each pasted one. */
    @Query(value = "select r from OutreachReply r left join fetch r.recordedBy where r.draft.id = :draftId order by r.receivedAt desc, r.id desc",
            countQuery = "select count(r) from OutreachReply r where r.draft.id = :draftId")
    Page<OutreachReply> findByDraft(@Param("draftId") UUID draftId, Pageable pageable);
}
