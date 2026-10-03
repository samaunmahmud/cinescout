package com.cinescout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A guest's call on a venue through a director link, under the name they gave. One per name per venue (any
 * capitalisation): answering again replaces it. Written only by {@code DirectorResponseRepository.upsert}.
 */
@Entity
@Table(name = "director_responses")
public class DirectorResponse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private Location location;

    @Column(name = "guest_name", nullable = false)
    private String guestName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DirectorVerdict verdict;

    private String comment;

    protected DirectorResponse() {
    }

    public Location getLocation() {
        return location;
    }

    public String getGuestName() {
        return guestName;
    }

    public DirectorVerdict getVerdict() {
        return verdict;
    }

    public String getComment() {
        return comment;
    }
}
