package com.cinescout.repository;

import com.cinescout.domain.LocationPhoto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LocationPhotoRepository extends JpaRepository<LocationPhoto, UUID> {

    /** A venue's photos in the order they were taken up, with who uploaded each. */
    @Query(value = "select p from LocationPhoto p left join fetch p.uploadedBy where p.location.id = :locationId order by p.createdAt asc, p.id asc",
            countQuery = "select count(p) from LocationPhoto p where p.location.id = :locationId")
    Page<LocationPhoto> findByLocation(@Param("locationId") UUID locationId, Pageable pageable);

    long countByLocationId(UUID locationId);

    /** The photo with its venue, scene and project, for the access check. */
    @Query("select p from LocationPhoto p join fetch p.location l join fetch l.scene s join fetch s.project where p.id = :id")
    Optional<LocationPhoto> findWithProject(@Param("id") UUID id);
}
