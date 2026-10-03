package com.cinescout.domain;

/** How far booking a venue for a day has got, or that it cannot be had that day. */
public enum AvailabilityState {
    /** Asked about, nothing promised. */
    PENCILLED,
    /** Held for the production, usually until a date. */
    HELD,
    /** Booked. */
    CONFIRMED,
    /** The venue cannot be had that day. */
    UNAVAILABLE;

    /** Whether a hold-expiry date means anything in this state. */
    public boolean canExpire() {
        return this == PENCILLED || this == HELD;
    }
}
