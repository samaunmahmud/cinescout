package com.cinescout.repository;

import com.cinescout.domain.DirectorResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DirectorResponseRepository extends JpaRepository<DirectorResponse, UUID> {

    /** Records a guest's call, replacing the one they gave before under the same name (any capitalisation). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into director_responses (location_id, guest_name, verdict, comment)
            values (:locationId, :guestName, :verdict, :comment)
            on conflict (location_id, lower(guest_name))
            do update set guest_name = excluded.guest_name, verdict = excluded.verdict, comment = excluded.comment""",
            nativeQuery = true)
    void upsert(@Param("locationId") UUID locationId, @Param("guestName") String guestName,
                @Param("verdict") String verdict, @Param("comment") String comment);

    /** The calls on these venues, latest first. */
    @Query("select r from DirectorResponse r where r.location.id in :locationIds order by r.updatedAt desc, r.id desc")
    List<DirectorResponse> findByLocations(@Param("locationIds") Collection<UUID> locationIds);

    @Query(value = "select r from DirectorResponse r where r.location.id = :locationId order by r.updatedAt desc, r.id desc",
            countQuery = "select count(r) from DirectorResponse r where r.location.id = :locationId")
    Page<DirectorResponse> findByLocation(@Param("locationId") UUID locationId, Pageable pageable);

    /** The calls on a scene's venues, latest first. */
    @Query(value = "select r from DirectorResponse r join fetch r.location l where l.scene.id = :sceneId order by r.updatedAt desc, r.id desc",
            countQuery = "select count(r) from DirectorResponse r where r.location.scene.id = :sceneId")
    Page<DirectorResponse> findByScene(@Param("sceneId") UUID sceneId, Pageable pageable);
}
