package com.cinescout.repository;

import com.cinescout.domain.LocationAgreement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LocationAgreementRepository extends JpaRepository<LocationAgreement, UUID> {

    /** A venue's versions, newest first. Access is checked on the venue before this is asked. */
    @Query(value = "select a from LocationAgreement a left join fetch a.createdBy where a.location.id = :locationId order by a.version desc",
            countQuery = "select count(a) from LocationAgreement a where a.location.id = :locationId")
    Page<LocationAgreement> findByLocation(@Param("locationId") UUID locationId, Pageable pageable);

    @Query("select coalesce(max(a.version), 0) from LocationAgreement a where a.location.id = :locationId")
    int latestVersion(@Param("locationId") UUID locationId);

    @Query("select count(a) from LocationAgreement a where a.location.id = :locationId")
    long countByLocation(@Param("locationId") UUID locationId);

    /** The agreement with its venue, only if the venue's project is one {@code userId} is a member of. */
    @Query("""
            select a from LocationAgreement a join fetch a.location l join l.scene s join s.project p
            where a.id = :id and exists (select m.id from ProjectMember m where m.project = p and m.user.id = :userId)""")
    Optional<LocationAgreement> findVisible(@Param("id") UUID id, @Param("userId") UUID userId);
}
