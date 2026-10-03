package com.cinescout.repository;

import com.cinescout.domain.VenueAvailability;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenueAvailabilityRepository extends JpaRepository<VenueAvailability, UUID> {

    /** A venue's days, earliest first. Access is checked on the venue before this is asked. */
    @Query(value = "select a from VenueAvailability a left join fetch a.setBy where a.location.id = :locationId order by a.day asc",
            countQuery = "select count(a) from VenueAvailability a where a.location.id = :locationId")
    Page<VenueAvailability> findByLocation(@Param("locationId") UUID locationId, Pageable pageable);

    @Query("select a from VenueAvailability a where a.location.id = :locationId and a.day = :day")
    Optional<VenueAvailability> findDay(@Param("locationId") UUID locationId, @Param("day") LocalDate day);

    /** Every day recorded at any of a project's venues, each with its venue, for the schedule. */
    @Query("select a from VenueAvailability a join fetch a.location l where l.scene.project.id = :projectId order by a.day asc")
    List<VenueAvailability> findByProject(@Param("projectId") UUID projectId);
}
