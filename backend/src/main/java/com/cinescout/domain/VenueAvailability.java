package com.cinescout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** A venue's state on one day: pencilled, held (perhaps until a date), confirmed or unavailable. */
@Entity
@Table(name = "venue_availability")
public class VenueAvailability extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private Location location;

    @Column(nullable = false, updatable = false)
    private LocalDate day;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AvailabilityState state;

    /** The day a pencil or hold lapses unless renewed; null for none, and always null in the other states. */
    @Column(name = "hold_expires_on")
    private LocalDate holdExpiresOn;

    private String note;

    /** Who set it last; null once that account is gone. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_by")
    private User setBy;

    protected VenueAvailability() {
    }

    public VenueAvailability(Location location, LocalDate day) {
        this.location = location;
        this.day = day;
    }

    public Location getLocation() { return location; }
    public LocalDate getDay() { return day; }
    public AvailabilityState getState() { return state; }
    public LocalDate getHoldExpiresOn() { return holdExpiresOn; }
    public String getNote() { return note; }
    public User getSetBy() { return setBy; }

    /** Sets everything at once; an expiry date is dropped in a state that cannot expire. */
    public void set(AvailabilityState state, LocalDate holdExpiresOn, String note, User setBy) {
        this.state = state;
        this.holdExpiresOn = state.canExpire() ? holdExpiresOn : null;
        this.note = note;
        this.setBy = setBy;
    }
}
