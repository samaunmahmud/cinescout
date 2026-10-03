package com.cinescout.repository;

import com.cinescout.domain.LibraryVenue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LibraryVenueRepository extends JpaRepository<LibraryVenue, UUID> {

    Optional<LibraryVenue> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<LibraryVenue> findByOwnerIdAndSourceLocationId(UUID ownerId, UUID sourceLocationId);

    /**
     * The owner's venues, newest first. {@code search} (a lower-case LIKE pattern, '%' for all) matches the name,
     * address, notes or a tag; {@code tag} (lower case, null for any) keeps the venues tagged with it.
     */
    @Query(value = """
            select * from library_venues v
            where v.owner_id = :ownerId
              and (lower(v.name) like :search or lower(coalesce(v.address, '')) like :search or lower(coalesce(v.notes, '')) like :search
                   or exists (select 1 from jsonb_array_elements_text(v.tags) t where lower(t) like :search))
              and (cast(:tag as text) is null or exists (select 1 from jsonb_array_elements_text(v.tags) t where lower(t) = cast(:tag as text)))
            order by v.created_at desc, v.id desc""",
            countQuery = """
            select count(*) from library_venues v
            where v.owner_id = :ownerId
              and (lower(v.name) like :search or lower(coalesce(v.address, '')) like :search or lower(coalesce(v.notes, '')) like :search
                   or exists (select 1 from jsonb_array_elements_text(v.tags) t where lower(t) like :search))
              and (cast(:tag as text) is null or exists (select 1 from jsonb_array_elements_text(v.tags) t where lower(t) = cast(:tag as text)))""",
            nativeQuery = true)
    Page<LibraryVenue> search(@Param("ownerId") UUID ownerId, @Param("search") String search, @Param("tag") String tag, Pageable pageable);

    /** Each tag the owner uses (as first written), with how many venues carry it, most used first. */
    @Query(value = """
            select min(t) as tag, count(*) as venues from library_venues v, jsonb_array_elements_text(v.tags) t
            where v.owner_id = :ownerId
            group by lower(t)
            order by count(*) desc, lower(t) asc
            limit 100""", nativeQuery = true)
    List<TagCount> findTags(@Param("ownerId") UUID ownerId);

    interface TagCount {
        String getTag();

        long getVenues();
    }
}
